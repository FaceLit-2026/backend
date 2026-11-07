package com.FaceLit.backend.academic.service.academic;

import java.util.List;
import java.util.UUID;

import com.FaceLit.backend.academic.dto.request.academic.ChipRequestDTO;
import com.FaceLit.backend.academic.dto.response.academic.ChipResponseDTO;

public interface ChipService {

    ChipResponseDTO create(UUID idProgram, ChipRequestDTO dto);

    List<ChipResponseDTO> findByProgram(UUID idProgram);

    ChipResponseDTO findById(UUID idChip);

    List<ChipResponseDTO> searchByCode(String code);

    ChipResponseDTO update(UUID idChip, ChipRequestDTO dto);

    ChipResponseDTO reactivate(UUID idChip);

    ChipResponseDTO delete(UUID idChip, String reason);
}