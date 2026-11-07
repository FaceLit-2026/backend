package com.FaceLit.backend.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;

import com.FaceLit.backend.auth.dto.request.roleandpermission.LoginRequestDTO;
import com.FaceLit.backend.shared.exception.GlobalExceptionHandler;

class LoginContractTest {

    @Test
    void shouldAcceptEmailAliasForLoginRequest() {
        LoginRequestDTO dto = new LoginRequestDTO();
        dto.setEmail("usuario@correo.com");
        dto.setPassword("Secret123!");

        assertEquals("usuario@correo.com", dto.resolveIdentifier());
        assertEquals("Secret123!", dto.getPassword());
    }

    @Test
    void shouldHandleMalformedJsonAsBadRequest() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        HttpInputMessage input = new MockHttpInputMessage("{bad json".getBytes());
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("Malformed JSON", input);

        var response = handler.handleHttpMessageNotReadable(ex);

        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("JSON inválido o payload incorrecto.", response.getBody().get("message"));
    }
}
