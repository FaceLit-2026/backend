package com.FaceLit.backend.auth.dto.response.security;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserListResponseDTO {

    private UUID userId;
    private String firstName;
    private String lastName;
    private String email;
    private String documentNumber;
    // Rol actual del usuario — puede ser APPRENTICE, INSTRUCTOR o ADMINISTRATOR
    private String currentRole;

}
