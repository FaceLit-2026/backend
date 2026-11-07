package com.FaceLit.backend.auth.service.security;

import com.FaceLit.backend.auth.dto.request.security.CoordinatorBootstrapRequestDTO;

public interface CoordinatorBootstrapService {
    enum Outcome { CREATED, ROTATED, UNCHANGED }
    Outcome initialize(CoordinatorBootstrapRequestDTO request);
    void verifyNoPublishedCredential();
}
