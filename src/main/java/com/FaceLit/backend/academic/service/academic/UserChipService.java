package com.FaceLit.backend.academic.service.academic;

import java.util.List;
import java.util.UUID;

import com.FaceLit.backend.academic.dto.request.academic.TransferChipRequestDTO;
import com.FaceLit.backend.academic.dto.request.academic.UserChipRequestDTO;
import com.FaceLit.backend.academic.dto.response.academic.UserChipResponseDTO;

public interface UserChipService {

    UserChipResponseDTO assignInitialChip(UUID idChip, UserChipRequestDTO dto);
    List<UserChipResponseDTO> findApprenticesByChip(UUID idChip);
    List<UserChipResponseDTO> findApprenticesByChips(List<UUID> idChips);
    UserChipResponseDTO getActiveChipByUser(UUID idUser);
    List<UserChipResponseDTO> getChipHistoryByUser(UUID idUser);
    List<UserChipResponseDTO> getTransferTargets(UUID idUser);
    UserChipResponseDTO updateApprentice(UUID idUser, UserChipRequestDTO dto);
    UserChipResponseDTO transferChip(UUID idUser, TransferChipRequestDTO dto);
}
