package com.FaceLit.backend.academic.service.serviceImpl.academic;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.lang.NonNull;

import com.FaceLit.backend.academic.dto.request.academic.ChipRequestDTO;
import com.FaceLit.backend.academic.dto.response.academic.ChipResponseDTO;
import com.FaceLit.backend.academic.exception.AcademicException;
import com.FaceLit.backend.academic.model.academic.ChangeHistory;
import com.FaceLit.backend.academic.model.academic.Chip;
import com.FaceLit.backend.academic.model.academic.Program;
import com.FaceLit.backend.academic.model.enums.AcademicState;
import com.FaceLit.backend.academic.model.enums.ChangeAction;
import com.FaceLit.backend.academic.repository.ChangeHistoryRepository;
import com.FaceLit.backend.academic.repository.ChipRepository;
import com.FaceLit.backend.academic.repository.ProgramRepository;
import com.FaceLit.backend.academic.repository.UserChipRepository;
import com.FaceLit.backend.academic.service.academic.ChipService;
import com.FaceLit.backend.notification.dto.request.MonolithEventRequestDTO;
import com.FaceLit.backend.notification.service.NotificationService;

@Service
// Facade de fichas: concentra validaciones, persistencia y bitácora detrás del contrato del servicio.
public class ChipServiceImpl implements ChipService {

    private final ChipRepository chipRepository;
    private final ProgramRepository programRepository;
    private final UserChipRepository userChipRepository;
    private final ChangeHistoryRepository changeHistoryRepository;
    private final NotificationService notificationService;

    public ChipServiceImpl(
            ChipRepository chipRepository,
            ProgramRepository programRepository,
            UserChipRepository userChipRepository,
            ChangeHistoryRepository changeHistoryRepository,
            NotificationService notificationService) {
        this.chipRepository = chipRepository;
        this.programRepository = programRepository;
        this.userChipRepository = userChipRepository;
        this.changeHistoryRepository = changeHistoryRepository;
        this.notificationService = notificationService;
    }

    @Override
    @Transactional
    public ChipResponseDTO create(UUID idProgram, ChipRequestDTO dto) {
        Program program = getProgram(idProgram);
        if (program.getState() != AcademicState.ACTIVE) {
            throw new AcademicException("No se puede crear una ficha en un programa inactivo");
        }
        String code = dto.getChipCode().trim();
        if (chipRepository.existsByChipCode(code)) {
            throw new AcademicException("Ya existe una ficha con ese código");
        }

        Chip chip = new Chip();
        chip.setProgram(program);
        chip.setChipCode(code);
        chip.setState(AcademicState.ACTIVE);
        chip = chipRepository.saveAndFlush(chip);
        record(chip, null, chip.getChipCode(), ChangeAction.CREATE);
        return new ChipResponseDTO(chip);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChipResponseDTO> findByProgram(UUID idProgram) {
        getProgram(idProgram);
        return chipRepository.findByProgram_IdProgram(idProgram).stream()
                .map(ChipResponseDTO::new)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ChipResponseDTO findById(UUID idChip) {
        return new ChipResponseDTO(getChip(idChip));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChipResponseDTO> searchByCode(String code) {
        if (code == null || code.isBlank()) return List.of();
        return chipRepository.findByChipCodeContainingIgnoreCase(code.trim()).stream()
                .map(ChipResponseDTO::new).toList();
    }

    @Override
    @Transactional
    public ChipResponseDTO update(UUID idChip, ChipRequestDTO dto) {
        Chip chip = getChip(idChip);
        String code = dto.getChipCode().trim();
        if (!chip.getChipCode().equals(code) && chipRepository.existsByChipCode(code)) {
            throw new AcademicException("Ya existe una ficha con ese código");
        }
        String oldCode = chip.getChipCode();
        chip.setChipCode(code);
        chip = chipRepository.saveAndFlush(chip);
        record(chip, oldCode, code, ChangeAction.UPDATE);
        return new ChipResponseDTO(chip);
    }

    @Override
    @Transactional
    public ChipResponseDTO reactivate(UUID idChip) {
        Chip chip = getChip(idChip);
        if (chip.getState() == AcademicState.ACTIVE) {
            throw new AcademicException("La ficha ya está activa");
        }
        if (chip.getProgram().getState() != AcademicState.ACTIVE) {
            throw new AcademicException("No se puede reactivar una ficha de un programa inactivo");
        }
        chip.setState(AcademicState.ACTIVE);
        chip.setDeactivationReason(null);
        chip = chipRepository.saveAndFlush(chip);
        record(chip, AcademicState.INACTIVE.name(), AcademicState.ACTIVE.name(), ChangeAction.REACTIVATE);
        notifyAcademicRemoval(
                "academic_entity_activated",
                "Ficha activada",
                "Se activo la ficha " + chip.getChipCode()
                        + " del programa " + chip.getProgram().getProgramName() + ".",
                chip);
        return new ChipResponseDTO(chip);
    }

    @Override
    @Transactional
    public ChipResponseDTO delete(UUID idChip, String reason) {
        Chip chip = getChip(idChip);
        long assignmentCount = userChipRepository.countByChip_IdChip(idChip);
        if (assignmentCount == 0) {
            ChipResponseDTO response = new ChipResponseDTO(chip);
            record(chip, chip.getState().name(), null, ChangeAction.DELETE);
            chipRepository.delete(chip);
            notifyAcademicRemoval(
                    "academic_entity_deleted",
                    "Ficha eliminada",
                    "Se elimino permanentemente la ficha " + chip.getChipCode()
                            + " del programa " + chip.getProgram().getProgramName() + ".",
                    chip);
            return response;
        }
        if (chip.getState() == AcademicState.ACTIVE && assignmentCount > 0) {
            chip.setState(AcademicState.INACTIVE);
            chip.setDeactivationReason(reason == null || reason.isBlank() ? null : reason.trim());
            chip = chipRepository.saveAndFlush(chip);
            record(chip, AcademicState.ACTIVE.name(), AcademicState.INACTIVE.name(), ChangeAction.DEACTIVATE);
            notifyAcademicRemoval(
                    "academic_entity_deactivated",
                    "Ficha desactivada",
                    "Se desactivo la ficha " + chip.getChipCode()
                            + " del programa " + chip.getProgram().getProgramName() + ".",
                    chip);
            return new ChipResponseDTO(chip);
        }

        if (userChipRepository.countByChip_IdChip(idChip) > 0) {
            notifyDeleteBlocked(chip, assignmentCount);
            throw new AcademicException("No se puede eliminar la ficha porque tiene aprendices asociados");
        }

        record(chip, AcademicState.INACTIVE.name(), null, ChangeAction.DELETE);
        chipRepository.delete(chip);
        return new ChipResponseDTO(chip);
    }

    private Program getProgram(@NonNull UUID idProgram) {
        UUID requiredId = Objects.requireNonNull(idProgram, "idProgram");
        return programRepository.findById(requiredId)
                .orElseThrow(() -> new AcademicException("Programa no encontrado"));
    }

    private Chip getChip(@NonNull UUID idChip) {
        UUID requiredId = Objects.requireNonNull(idChip, "idChip");
        return chipRepository.findById(requiredId)
                .orElseThrow(() -> new AcademicException("Ficha no encontrada"));
    }

    private void record(Chip chip, String oldValue, String newValue, ChangeAction action) {
        ChangeHistory history = new ChangeHistory();
        history.setEntityName("chip");
        history.setEntityId(chip.getIdChip());
        history.setFieldName("chip");
        history.setOldValue(oldValue);
        history.setNewValue(newValue);
        history.setAction(action);
        changeHistoryRepository.save(history);
    }

    private void notifyDeleteBlocked(Chip chip, long assignmentCount) {
        try {
            notificationService.createIndependentCoordinatorEvent(new MonolithEventRequestDTO(
                    "academic_delete_blocked",
                    "Intento de eliminacion bloqueado",
                    "No fue posible eliminar la ficha " + chip.getChipCode()
                            + " porque tiene " + assignmentCount + " aprendices asociados.",
                    null,
                    chip.getIdChip(),
                    "chip",
                    null,
                    "{\"entityType\":\"chip\",\"entityId\":\"" + chip.getIdChip() + "\"}"));
        } catch (RuntimeException ignored) {
            // La respuesta academica no debe depender del registro de notificaciones.
        }
    }

    private void notifyAcademicRemoval(String type, String title, String message, Chip chip) {
        try {
            notificationService.createCoordinatorEvent(new MonolithEventRequestDTO(
                    type,
                    title,
                    message,
                    null,
                    chip.getIdChip(),
                    "chip",
                    null,
                    "{\"entityType\":\"chip\",\"entityId\":\"" + chip.getIdChip()
                            + "\",\"fichaNumber\":\"" + safe(chip.getChipCode())
                            + "\",\"programName\":\"" + safe(chip.getProgram().getProgramName()) + "\"}"));
        } catch (RuntimeException ignored) {
            // La respuesta academica no debe depender del registro de notificaciones.
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
