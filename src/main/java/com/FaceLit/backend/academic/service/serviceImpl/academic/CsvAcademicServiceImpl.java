package com.FaceLit.backend.academic.service.serviceImpl.academic;

import com.FaceLit.backend.academic.dto.response.academic.CsvUploadResponseDTO;
import com.FaceLit.backend.academic.dto.response.academic.PendingTransferResponseDTO;
import com.FaceLit.backend.academic.dto.response.academic.UserChipResponseDTO;
import com.FaceLit.backend.academic.model.academic.Chip;
import com.FaceLit.backend.academic.model.academic.Program;
import com.FaceLit.backend.academic.service.academic.CsvAcademicService;
import com.FaceLit.backend.academic.service.csv.CsvDocumentParser;
import com.FaceLit.backend.academic.service.csv.CsvImportNotifier;
import com.FaceLit.backend.academic.service.csv.CsvInstructorImporter;
import com.FaceLit.backend.academic.service.csv.CsvLearnerImporter;
import com.FaceLit.backend.academic.service.csv.CsvRow;
import com.FaceLit.backend.academic.service.csv.CsvStructureImporter;
import com.FaceLit.backend.academic.service.csv.CsvTransferService;
import com.FaceLit.backend.shared.constants.AppConstants;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Transactional facade: preserves the CSV API, import order and result contract. */
@Service
public class CsvAcademicServiceImpl implements CsvAcademicService {
    private final CsvImportNotifier notifier;
    private final CsvStructureImporter structureImporter;
    private final CsvInstructorImporter instructorImporter;
    private final CsvLearnerImporter learnerImporter;
    private final CsvTransferService transfers;
    private final CsvDocumentParser parser;

    public CsvAcademicServiceImpl(
            CsvImportNotifier notifier,
            CsvStructureImporter structureImporter,
            CsvInstructorImporter instructorImporter,
            CsvLearnerImporter learnerImporter,
            CsvTransferService transfers,
            CsvDocumentParser parser) {
        this.notifier = notifier;
        this.structureImporter = structureImporter;
        this.instructorImporter = instructorImporter;
        this.learnerImporter = learnerImporter;
        this.transfers = transfers;
        this.parser = parser;
    }

    @Override
    public byte[] template() {
        return (AppConstants.CSV_TEMPLATE_HEADER + "\n"
                + "programa,,Análisis y Desarrollo de Software,,,ADSO,,\n"
                + "ficha,,,,,ADSO,2825551,\n"
                + "aprendiz,100234,Juan,Perez,juan.perez@correo.com,,2825551,\n"
                + "instructor,1029384756,Laura,Gomez,laura.gomez@correo.com,ADSO,2825551,especifico\n"
                + "instructor,1050607080,Carlos,Ruiz,carlos.ruiz@correo.com,,,transversal\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    @Override
    @Transactional
    public CsvUploadResponseDTO upload(MultipartFile file) {
        List<CsvRow> rows = parser.readAndValidate(file);
        CsvUploadResponseDTO result = new CsvUploadResponseDTO();
        Map<String, Program> programs = new HashMap<>();
        Map<String, Chip> chips = new HashMap<>();

        rows.stream().filter(row -> row.value("tipo").equals("programa"))
                .forEach(row -> structureImporter.processProgram(row, programs, result));
        rows.stream().filter(row -> row.value("tipo").equals("ficha"))
                .forEach(row -> structureImporter.processChip(row, programs, chips, result));
        instructorImporter.processInstructors(rows, programs, chips, result);
        rows.stream().filter(row -> row.value("tipo").equals("aprendiz"))
                .forEach(row -> learnerImporter.processApprentice(row, chips, result));
        notifier.notifyCsvUpload(result);
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PendingTransferResponseDTO> pendingTransfers() {
        return transfers.pendingTransfers();
    }

    @Override
    @Transactional
    public UserChipResponseDTO acceptPendingTransfer(UUID idPendingTransfer) {
        return transfers.acceptPendingTransfer(idPendingTransfer);
    }

    @Override
    @Transactional
    public void cancelPendingTransfer(UUID idPendingTransfer) {
        transfers.cancelPendingTransfer(idPendingTransfer);
    }
}
