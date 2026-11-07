package com.FaceLit.backend.academic.service.serviceImpl.academic;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.lang.NonNull;

import com.FaceLit.backend.academic.dto.request.academic.ProgramRequestDTO;
import com.FaceLit.backend.academic.dto.response.academic.ProgramResponseDTO;
import com.FaceLit.backend.academic.exception.AcademicException;
import com.FaceLit.backend.academic.model.academic.ChangeHistory;
import com.FaceLit.backend.academic.model.academic.Chip;
import com.FaceLit.backend.academic.model.academic.Program;
import com.FaceLit.backend.academic.model.enums.AcademicState;
import com.FaceLit.backend.academic.model.enums.ChangeAction;
import com.FaceLit.backend.academic.repository.ChangeHistoryRepository;
import com.FaceLit.backend.academic.repository.ChipRepository;
import com.FaceLit.backend.academic.repository.InstructorProgramRepository;
import com.FaceLit.backend.academic.repository.ProgramRepository;
import com.FaceLit.backend.academic.service.academic.ProgramService;
import com.FaceLit.backend.notification.dto.request.MonolithEventRequestDTO;
import com.FaceLit.backend.notification.service.NotificationService;

@Service
// Facade de programas: el controller usa este servicio y no conoce repositorios ni reglas de persistencia.
public class ProgramServiceImpl implements ProgramService {

    private final ProgramRepository programRepository;
    private final ChipRepository chipRepository;
    private final InstructorProgramRepository instructorProgramRepository;
    private final ChangeHistoryRepository changeHistoryRepository;
    private final NotificationService notificationService;

    public ProgramServiceImpl(
            ProgramRepository programRepository,
            ChipRepository chipRepository,
            InstructorProgramRepository instructorProgramRepository,
            ChangeHistoryRepository changeHistoryRepository,
            NotificationService notificationService) {
        this.programRepository = programRepository;
        this.chipRepository = chipRepository;
        this.instructorProgramRepository = instructorProgramRepository;
        this.changeHistoryRepository = changeHistoryRepository;
        this.notificationService = notificationService;
    }

    @Override
    @Transactional
    public ProgramResponseDTO create(ProgramRequestDTO dto) {
        String name = dto.getProgramName().trim();
        String code = dto.getProgramCode().trim();

        if (programRepository.existsByProgramNameIgnoreCase(name)) {
            throw new AcademicException("Ya existe un programa con ese nombre");
        }
        if (programRepository.existsByProgramCodeIgnoreCase(code)) {
            throw new AcademicException("Ya existe un programa con ese código");
        }

        Program program = new Program();
        program.setProgramName(name);
        program.setProgramCode(code);
        program.setState(AcademicState.ACTIVE);
        program = programRepository.saveAndFlush(program);
        record(program, "program", null, program.getProgramName(), ChangeAction.CREATE);
        return new ProgramResponseDTO(program, chipRepository.findByProgram_IdProgram(program.getIdProgram()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProgramResponseDTO> findAll() {
        List<Program> programs = programRepository.findAll();
        if (programs.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<Chip>> chipsByProgram = chipRepository
                .findByProgram_IdProgramIn(programs.stream().map(Program::getIdProgram).toList())
                .stream()
                .collect(Collectors.groupingBy(chip -> chip.getProgram().getIdProgram()));

        return programs.stream()
                .map(program -> new ProgramResponseDTO(program, chipsByProgram.getOrDefault(program.getIdProgram(), List.of())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProgramResponseDTO> searchByName(String name) {
        if (name == null || name.isBlank()) return List.of();
        return programRepository.findByProgramNameContainingIgnoreCase(name.trim()).stream()
                .map(program -> new ProgramResponseDTO(program, chipRepository.findByProgram_IdProgram(program.getIdProgram())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ProgramResponseDTO findByCode(String code) {
        return programRepository.findByProgramCodeIgnoreCase(code.trim())
                .map(program -> new ProgramResponseDTO(program, chipRepository.findByProgram_IdProgram(program.getIdProgram())))
                .orElseThrow(() -> new AcademicException("Programa no encontrado", org.springframework.http.HttpStatus.NOT_FOUND));
    }

    @Override
    @Transactional(readOnly = true)
    public ProgramResponseDTO findById(UUID idProgram) {
        Program program = getProgram(idProgram);
        return new ProgramResponseDTO(program, chipRepository.findByProgram_IdProgram(program.getIdProgram()));
    }

    @Override
    @Transactional
    public ProgramResponseDTO update(UUID idProgram, ProgramRequestDTO dto) {
        Program program = getProgram(idProgram);
        String name = dto.getProgramName().trim();
        String code = dto.getProgramCode().trim();

        if (programRepository.existsByProgramNameIgnoreCaseAndIdProgramNot(name, idProgram)) {
            throw new AcademicException("Ya existe un programa con ese nombre");
        }
        if (programRepository.existsByProgramCodeIgnoreCaseAndIdProgramNot(code, idProgram)) {
            throw new AcademicException("Ya existe un programa con ese código");
        }

        String oldValue = program.getProgramName() + " / " + program.getProgramCode();
        program.setProgramName(name);
        program.setProgramCode(code);
        program = programRepository.saveAndFlush(program);
        record(program, "program", oldValue, name + " / " + code, ChangeAction.UPDATE);
        return new ProgramResponseDTO(program, chipRepository.findByProgram_IdProgram(program.getIdProgram()));
    }

    @Override
    @Transactional
    public ProgramResponseDTO reactivate(UUID idProgram) {
        Program program = getProgram(idProgram);
        if (program.getState() == AcademicState.ACTIVE) {
            throw new AcademicException("El programa ya está activo");
        }
        program.setState(AcademicState.ACTIVE);
        program.setDeactivationReason(null);
        program = programRepository.saveAndFlush(program);
        record(program, "program", AcademicState.INACTIVE.name(), AcademicState.ACTIVE.name(), ChangeAction.REACTIVATE);
        notifyAcademicRemoval(
                "academic_entity_activated",
                "Programa activado",
                "Se activo el programa " + program.getProgramName() + " (" + program.getProgramCode() + ").",
                program);
        return new ProgramResponseDTO(program, chipRepository.findByProgram_IdProgram(program.getIdProgram()));
    }

    @Override
    @Transactional
    public ProgramResponseDTO delete(UUID idProgram, String reason) {
        Program program = getProgram(idProgram);
        long chips = chipRepository.countByProgram_IdProgram(idProgram);
        long instructors = instructorProgramRepository.countByProgram_IdProgram(idProgram);
        if (chips > 0 || instructors > 0) {
            notifyDeleteBlocked(program, chips, instructors);
            throw new AcademicException("No se puede inactivar ni eliminar el programa porque tiene fichas o instructores asociados");
        }

        if (program.getState() == AcademicState.ACTIVE) {
            program.setState(AcademicState.INACTIVE);
            program.setDeactivationReason(reason == null || reason.isBlank() ? null : reason.trim());
            program = programRepository.saveAndFlush(program);
            record(program, "program", AcademicState.ACTIVE.name(), AcademicState.INACTIVE.name(), ChangeAction.DEACTIVATE);
            notifyAcademicRemoval(
                    "academic_entity_deactivated",
                    "Programa desactivado",
                    "Se desactivo el programa " + program.getProgramName() + " (" + program.getProgramCode() + ").",
                    program);
            return new ProgramResponseDTO(program);
        }

        record(program, "program", AcademicState.INACTIVE.name(), null, ChangeAction.DELETE);
        programRepository.delete(program);
        notifyAcademicRemoval(
                "academic_entity_deleted",
                "Programa eliminado",
                "Se elimino permanentemente el programa " + program.getProgramName() + " (" + program.getProgramCode() + ").",
                program);
        return new ProgramResponseDTO(program, List.of());
    }

    private Program getProgram(@NonNull UUID idProgram) {
        UUID requiredId = Objects.requireNonNull(idProgram, "idProgram");
        return programRepository.findById(requiredId)
                .orElseThrow(() -> new AcademicException("Programa no encontrado"));
    }

    private void record(Program program, String entityName, String oldValue, String newValue, ChangeAction action) {
        ChangeHistory history = new ChangeHistory();
        history.setEntityName(entityName);
        history.setEntityId(program.getIdProgram());
        history.setFieldName("program");
        history.setOldValue(oldValue);
        history.setNewValue(newValue);
        history.setAction(action);
        changeHistoryRepository.save(history);
    }

    private void notifyDeleteBlocked(Program program, long chips, long instructors) {
        try {
            notificationService.createIndependentCoordinatorEvent(new MonolithEventRequestDTO(
                    "academic_delete_blocked",
                    "Intento de eliminacion bloqueado",
                    "No fue posible eliminar el programa " + program.getProgramName()
                            + " porque tiene " + chips + " fichas y " + instructors + " instructores asociados.",
                    null,
                    program.getIdProgram(),
                    "program",
                    null,
                    "{\"entityType\":\"program\",\"entityId\":\"" + program.getIdProgram() + "\"}"));
        } catch (RuntimeException ignored) {
            // La regla academica debe responder aunque falle el registro de la notificacion.
        }
    }

    private void notifyAcademicRemoval(String type, String title, String message, Program program) {
        try {
            notificationService.createCoordinatorEvent(new MonolithEventRequestDTO(
                    type,
                    title,
                    message,
                    null,
                    program.getIdProgram(),
                    "program",
                    null,
                    "{\"entityType\":\"program\",\"entityId\":\"" + program.getIdProgram()
                            + "\",\"programName\":\"" + safe(program.getProgramName())
                            + "\",\"programCode\":\"" + safe(program.getProgramCode()) + "\"}"));
        } catch (RuntimeException ignored) {
            // La regla academica debe responder aunque falle el registro de la notificacion.
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
