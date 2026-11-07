package com.FaceLit.backend.auth.dto.response.security;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.UUID;


@Getter
@AllArgsConstructor
public class ProfileResponseDTO {

    private UUID idUser;
    private String firstName;
    private String lastName;
    private String documentNumber;
    private String email;

}
