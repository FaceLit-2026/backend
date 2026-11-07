package com.FaceLit.backend.auth.service.security;

import java.util.UUID;
import com.FaceLit.backend.auth.dto.response.security.ProfileResponseDTO;

public interface ProfileService {

    ProfileResponseDTO getMyProfile(UUID userId);
}
