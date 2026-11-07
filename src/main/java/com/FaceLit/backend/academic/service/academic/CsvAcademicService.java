package com.FaceLit.backend.academic.service.academic;

import java.util.List;
import java.util.UUID;

import org.springframework.web.multipart.MultipartFile;

import com.FaceLit.backend.academic.dto.response.academic.CsvUploadResponseDTO;
import com.FaceLit.backend.academic.dto.response.academic.PendingTransferResponseDTO;
import com.FaceLit.backend.academic.dto.response.academic.UserChipResponseDTO;

public interface CsvAcademicService {

    byte[] template();

    CsvUploadResponseDTO upload(MultipartFile file);

    List<PendingTransferResponseDTO> pendingTransfers();

    UserChipResponseDTO acceptPendingTransfer(UUID idPendingTransfer);

    void cancelPendingTransfer(UUID idPendingTransfer);
}
