package com.FaceLit.backend.auth.dto.response.security;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.FaceLit.backend.auth.model.enums.AccountStatus;
import com.FaceLit.backend.auth.model.enums.RoleName;

public interface UserListProjection {
    UUID getUserId();
    String getFirstName();
    String getLastName();
    String getDocumentNumber();
    String getEmail();
    RoleName getRole();
    AccountStatus getAccountStatus();
    OffsetDateTime getRegistrationDate();
}
