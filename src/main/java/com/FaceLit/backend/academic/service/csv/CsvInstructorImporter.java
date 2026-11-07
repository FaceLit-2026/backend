package com.FaceLit.backend.academic.service.csv;

import com.FaceLit.backend.academic.dto.request.academic.InstructorRequestDTO;
import com.FaceLit.backend.academic.dto.response.academic.CsvUploadResponseDTO;
import com.FaceLit.backend.academic.model.academic.Chip;
import com.FaceLit.backend.academic.model.academic.Instructor;
import com.FaceLit.backend.academic.model.academic.InstructorChip;
import com.FaceLit.backend.academic.model.academic.InstructorProgram;
import com.FaceLit.backend.academic.model.academic.Program;
import com.FaceLit.backend.academic.model.enums.InstructorType;
import com.FaceLit.backend.academic.repository.ChipRepository;
import com.FaceLit.backend.academic.repository.InstructorChipRepository;
import com.FaceLit.backend.academic.repository.InstructorProgramRepository;
import com.FaceLit.backend.academic.repository.InstructorRepository;
import com.FaceLit.backend.academic.repository.ProgramRepository;
import com.FaceLit.backend.academic.service.academic.InstructorService;
import com.FaceLit.backend.auth.model.enums.RoleName;
import com.FaceLit.backend.auth.model.security.User;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Instructor CSV assignments and program links. */
@org.springframework.stereotype.Component
public class CsvInstructorImporter {
    private final ProgramRepository programRepository;
    private final ChipRepository chipRepository;
    private final InstructorRepository instructorRepository;
    private final InstructorProgramRepository instructorProgramRepository;
    private final InstructorChipRepository instructorChipRepository;
    private final InstructorService instructorService;
    private final CsvImportNotifier notifier;
    private final CsvUserImporter userImporter;

    public CsvInstructorImporter(
            ProgramRepository programRepository,
            ChipRepository chipRepository,
            InstructorRepository instructorRepository,
            InstructorProgramRepository instructorProgramRepository,
            InstructorChipRepository instructorChipRepository,
            InstructorService instructorService,
            CsvImportNotifier notifier,
            CsvUserImporter userImporter) {
        this.programRepository = programRepository;
        this.chipRepository = chipRepository;
        this.instructorRepository = instructorRepository;
        this.instructorProgramRepository = instructorProgramRepository;
        this.instructorChipRepository = instructorChipRepository;
        this.instructorService = instructorService;
        this.notifier = notifier;
        this.userImporter = userImporter;
    }

    public void processInstructors(List<CsvRow> rows, Map<String, Program> programs, Map<String, Chip> chips,
            CsvUploadResponseDTO result) {
        Map<String, List<CsvRow>> grouped = rows.stream().filter(row -> row.value("tipo").equals("instructor"))
                .collect(Collectors.groupingBy(row -> row.value("documento"), HashMap::new, Collectors.toList()));
        grouped.forEach((document, instructorRows) -> {
            CsvRow first = instructorRows.get(0);
            if (!CsvValidationRules.DOCUMENT.matcher(document).matches()) {
                result.getErroresDeReferencia().add(first.error("El documento de la fila " + first.number() + " debe tener entre 6 y 15 dígitos numéricos."));
                return;
            }
            String typeValue = first.value("instructor_tipo").toUpperCase(Locale.ROOT);
            InstructorType type = typeValue.equals("ESPECIFICO") ? InstructorType.ESPECIFICO
                    : typeValue.equals("TRANSVERSAL") ? InstructorType.TRANSVERSAL : null;
            if (type == null) {
                result.getErroresDeReferencia()
                        .add(first.error("El tipo de instructor de la fila " + first.number() + " no es válido."));
                return;
            }
            List<Program> instructorPrograms = new ArrayList<>();
            List<Chip> instructorChips = new ArrayList<>();
            boolean invalidProgramReference = false;
            boolean invalidChipReference = false;
            for (CsvRow row : instructorRows) {
                String programCode = row.value("programa_codigo").trim().toUpperCase(Locale.ROOT);
                String chipCode = row.value("ficha_codigo").trim();
                if (type == InstructorType.TRANSVERSAL) {
                    if (!programCode.isBlank()) {
                        result.getErroresDeReferencia().add(row.error("Un instructor transversal no debe indicar programa_codigo en la fila "
                                        + row.number() + "."));
                        invalidProgramReference = true;
                    }
                    if (!chipCode.isBlank()
                            && resolveInstructorChip(row, chipCode, chips, instructorChips, result) == null) {
                        invalidChipReference = true;
                    }
                    continue;
                }
                Program program = programs.get(programCode);
                if (program == null && !programCode.isBlank()) {
                    program = programRepository.findByProgramCodeIgnoreCase(programCode).orElse(null);
                }
                if (program == null) {
                    result.getErroresDeReferencia().add(row.error("El programa_codigo de la fila " + row.number()
                                    + " es obligatorio para un instructor específico y debe existir."));
                    invalidProgramReference = true;
                    continue;
                }
                UUID resolvedProgramId = program.getIdProgram();
                if (instructorPrograms.stream().noneMatch(item -> item.getIdProgram().equals(resolvedProgramId))) {
                    instructorPrograms.add(program);
                }
                Chip chip = resolveInstructorChip(row, chipCode, chips, instructorChips, result);
                if (!chipCode.isBlank() && chip == null) {
                    invalidChipReference = true;
                    continue;
                }
                if (chip != null && !chip.getProgram().getIdProgram().equals(resolvedProgramId)) {
                    result.getErroresDeReferencia().add(row.error("La ficha '" + chipCode + "' indicada en la fila " + row.number()
                                    + " no pertenece al programa_codigo '" + programCode + "'."));
                    invalidChipReference = true;
                }
            }
            if (invalidProgramReference || invalidChipReference
                    || (type == InstructorType.ESPECIFICO && instructorPrograms.isEmpty())) {
                return;
            }
            User user = userImporter.findOrCreateUser(first, result);
            if (user == null) {
                return;
            }
            userImporter.assignRole(user, RoleName.INSTRUCTOR);
            Instructor instructor = instructorRepository.findByUser_IdUser(user.getIdUser()).orElse(null);
            if (instructor == null) {
                InstructorRequestDTO dto = new InstructorRequestDTO();
                dto.setIdUser(user.getIdUser());
                dto.setInstructorType(type);
                dto.setProgramIds(instructorPrograms.stream().map(Program::getIdProgram).toList());
                instructorService.create(dto);
                instructorRepository.flush();
                instructor = instructorRepository.findByUser_IdUser(user.getIdUser()).orElseThrow();
                saveInstructorChips(instructor, instructorChips);
                result.getCreados()
                        .add(new CsvUploadResponseDTO.CsvRowResult(first.number(), "instructor", document + " creado"));
                return;
            }
            if (instructor.getInstructorType() != type) {
                result.getInconsistenciasBloqueadas()
                        .add(new CsvUploadResponseDTO.CsvRowError(first.number(), "instructor",
                                "El instructor " + document + " ya está registrado como "
                                        + instructor.getInstructorType().name()
                                        + ". Cambiar su tipo requiere confirmación manual.",
                                instructor.getInstructorType().name(), type.name()));
                return;
            }
            Set<UUID> existing = instructorProgramRepository.findByInstructor_IdInstructor(instructor.getIdInstructor()).stream()
                    .map(item -> item.getProgram().getIdProgram()).collect(Collectors.toSet());
            List<String> addedPrograms = new ArrayList<>();
            for (Program program : instructorPrograms) {
                if (!existing.contains(program.getIdProgram())) {
                    InstructorProgram relation = new InstructorProgram();
                    relation.setInstructor(instructor);
                    relation.setProgram(program);
                    instructorProgramRepository.save(relation);
                    existing.add(program.getIdProgram());
                    addedPrograms.add(program.getProgramName() + " (" + program.getProgramCode() + ")");
                }
            }
            if (!addedPrograms.isEmpty()) instructor.setUpdatedAt(java.time.OffsetDateTime.now());
            saveInstructorChips(instructor, instructorChips);
            notifier.notifyCsvInstructorPrograms(instructor, addedPrograms);
            result.getActualizados()
                    .add(new CsvUploadResponseDTO.CsvRowResult(first.number(), "instructor", document + " actualizado"));
        });
    }

    private Chip resolveInstructorChip(CsvRow row, String chipCode, Map<String, Chip> chips, List<Chip> instructorChips,
            CsvUploadResponseDTO result) {
        if (chipCode.isBlank()) {
            return null;
        }
        Chip chip = chips.get(chipCode);
        if (chip == null) {
            chip = chipRepository.findByChipCode(chipCode).orElse(null);
        }
        if (chip == null) {
            result.getErroresDeReferencia().add(row.error("La ficha '" + chipCode + "' indicada en la fila " + row.number() + " no existe."));
            return null;
        }
        UUID resolvedChipId = chip.getIdChip();
        if (instructorChips.stream().noneMatch(item -> item.getIdChip().equals(resolvedChipId))) {
            instructorChips.add(chip);
        }
        return chip;
    }

    private void saveInstructorChips(Instructor instructor, List<Chip> chips) {
        for (Chip chip : chips) {
            if (instructorChipRepository.existsByInstructor_IdInstructorAndChip_IdChipAndActiveTrue(
                    instructor.getIdInstructor(), chip.getIdChip())) {
                continue;
            }
            InstructorChip relation = new InstructorChip();
            relation.setInstructor(instructor);
            relation.setChip(chip);
            relation.setActive(true);
            instructorChipRepository.save(relation);
            instructor.setUpdatedAt(java.time.OffsetDateTime.now());
        }
    }

}
