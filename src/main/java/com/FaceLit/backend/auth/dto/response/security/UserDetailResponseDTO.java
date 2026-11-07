package com.FaceLit.backend.auth.dto.response.security;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserDetailResponseDTO {

    private UUID userId;
    private String firstName;
    private String lastName;
    private String documentNumber;
    private String email;
    private String role;
    private String accountStatus;
    private String sessionStatus;
    private OffsetDateTime registrationDate;
    private OffsetDateTime sessionExpiresAt;
    private boolean hasSession;
    private String chipCode;
    private String programName;
    private List<String> instructorChipCodes;
    private List<String> instructorProgramNames;
}
