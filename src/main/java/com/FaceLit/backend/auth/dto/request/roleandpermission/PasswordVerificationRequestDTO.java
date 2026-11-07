package com.FaceLit.backend.auth.dto.request.roleandpermission;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PasswordVerificationRequestDTO {

    @NotBlank
    private String password;
}
