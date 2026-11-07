package com.FaceLit.backend.auth.dto.request.security;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Administrative configuration input, never a public registration endpoint. */
public record CoordinatorBootstrapRequestDTO(
        @NotBlank @Pattern(regexp = "[0-9]{6,15}") String document,
        @Size(max = 60) String firstName,
        @Size(max = 60) String lastName,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank String password,
        boolean rotatePassword) {
    @Override public String toString() { return "CoordinatorBootstrapRequestDTO[redacted]"; }
}
