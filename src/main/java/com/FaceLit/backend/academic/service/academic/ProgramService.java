package com.FaceLit.backend.academic.service.academic;

import java.util.List;
import java.util.UUID;

import com.FaceLit.backend.academic.dto.request.academic.ProgramRequestDTO;
import com.FaceLit.backend.academic.dto.response.academic.ProgramResponseDTO;

public interface ProgramService {

    ProgramResponseDTO create(ProgramRequestDTO dto);

    List<ProgramResponseDTO> findAll();

    List<ProgramResponseDTO> searchByName(String name);

    ProgramResponseDTO findByCode(String code);

    ProgramResponseDTO findById(UUID idProgram);

    ProgramResponseDTO update(UUID idProgram, ProgramRequestDTO dto);

    ProgramResponseDTO reactivate(UUID idProgram);

    ProgramResponseDTO delete(UUID idProgram, String reason);
}