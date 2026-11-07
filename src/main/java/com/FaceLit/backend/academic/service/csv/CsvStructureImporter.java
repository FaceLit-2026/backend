package com.FaceLit.backend.academic.service.csv;

import com.FaceLit.backend.academic.dto.request.academic.ChipRequestDTO;
import com.FaceLit.backend.academic.dto.response.academic.CsvUploadResponseDTO;
import com.FaceLit.backend.academic.model.academic.Chip;
import com.FaceLit.backend.academic.model.academic.Program;
import com.FaceLit.backend.academic.model.enums.AcademicState;
import com.FaceLit.backend.academic.repository.ChipRepository;
import com.FaceLit.backend.academic.repository.ProgramRepository;
import com.FaceLit.backend.academic.service.academic.ChipService;
import java.util.Locale;
import java.util.Map;

/** Imports programs and training groups in their original order. */
@org.springframework.stereotype.Component
public class CsvStructureImporter {
    private final ProgramRepository programRepository;
    private final ChipRepository chipRepository;
    private final ChipService chipService;

    public CsvStructureImporter(
            ProgramRepository programRepository,
            ChipRepository chipRepository,
            ChipService chipService) {
        this.programRepository = programRepository;
        this.chipRepository = chipRepository;
        this.chipService = chipService;
    }

    public void processProgram(CsvRow row, Map<String, Program> programs, CsvUploadResponseDTO result) {
        String code = row.value("programa_codigo").toUpperCase(Locale.ROOT);
        String name = row.value("nombre");
        if (code.isBlank()) {
            result.getErroresDeReferencia().add(new CsvUploadResponseDTO.CsvRowError(row.number(), row.value("tipo"),
                    "El código de programa es obligatorio en la fila " + row.number() + ".", null, null));
            return;
        }
        if (!CsvValidationRules.PROGRAM_CODE.matcher(code).matches()) {
            result.getErroresDeReferencia().add(row.error("El código de programa de la fila " + row.number()
                    + " solo puede contener letras y números, sin espacios."));
            return;
        }
        // La plantilla oficial identifica el programa solo con programa_codigo.
        if (name.isBlank()) {
            name = code;
        }
        if (!CsvValidationRules.NAME.matcher(name).matches() || name.length() < 2 || name.length() > 100) {
            result.getErroresDeReferencia()
                    .add(row.error("El nombre del programa de la fila " + row.number() + " no es válido."));
            return;
        }
        Program program = programRepository.findByProgramCodeIgnoreCase(code).orElse(null);
        if (program == null) {
            program = new Program();
            program.setProgramCode(code);
            program.setState(AcademicState.ACTIVE);
            program.setProgramName(name);
            program = programRepository.save(program);
            result.getCreados().add(new CsvUploadResponseDTO.CsvRowResult(row.number(), "programa", code + " creado"));
        } else {
            program.setProgramName(name);
            program = programRepository.save(program);
            result.getActualizados()
                    .add(new CsvUploadResponseDTO.CsvRowResult(row.number(), "programa", code + " actualizado"));
        }
        programs.put(code, program);
    }

    public void processChip(CsvRow row, Map<String, Program> programs, Map<String, Chip> chips,
            CsvUploadResponseDTO result) {
        String code = row.value("ficha_codigo");
        String programCode = row.value("programa_codigo").toUpperCase(Locale.ROOT);
        if (!CsvValidationRules.CHIP_CODE.matcher(code).matches()) {
            result.getErroresDeReferencia().add(new CsvUploadResponseDTO.CsvRowError(row.number(), "ficha",
                    "El código de ficha de la fila " + row.number() + " debe tener 7 dígitos numéricos.", null, code));
            return;
        }
        Program program = programs.get(programCode);
        if (program == null) {
            program = programRepository.findByProgramCodeIgnoreCase(programCode).orElse(null);
        }
        if (program == null) {
            result.getErroresDeReferencia().add(
                    row.error("El programa '" + programCode + "' indicado en la fila " + row.number() + " no existe."));
            return;
        }
        Chip chip = chipRepository.findByChipCode(code).orElse(null);
        if (chip == null) {
            ChipRequestDTO dto = new ChipRequestDTO();
            dto.setIdProgram(program.getIdProgram());
            dto.setChipCode(code);
            chipService.create(program.getIdProgram(), dto);
            chip = chipRepository.findByChipCode(code).orElseThrow();
            result.getCreados().add(new CsvUploadResponseDTO.CsvRowResult(row.number(), "ficha", code + " creada"));
        } else if (!chip.getProgram().getIdProgram().equals(program.getIdProgram())) {
            result.getInconsistenciasBloqueadas()
                    .add(new CsvUploadResponseDTO.CsvRowError(row.number(), "ficha",
                            "La ficha " + code
                                    + " ya pertenece a otro programa. No se puede cambiar de programa por este medio.",
                            chip.getProgram().getProgramCode(), programCode));
            return;
        } else {
            result.getActualizados()
                    .add(new CsvUploadResponseDTO.CsvRowResult(row.number(), "ficha", code + " actualizada"));
        }
        chips.put(code, chip);
    }

}
