package com.FaceLit.backend.facial;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.FaceLit.backend.auth.service.roleandpermission.JwtService;
import com.FaceLit.backend.auth.service.serviceImpl.security.RecoveryRateLimiter;
import com.FaceLit.backend.config.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({FacialEmbeddingController.class, com.FaceLit.backend.shared.controller.HealthController.class})
@Import({SecurityConfig.class, JwtFilter.class, FacialEmbeddingRegistrationService.class})
class FacialEmbeddingHttpTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean JwtService jwt;
    @MockitoBean RecoveryRateLimiter limiter;
    @MockitoBean FacialEmbeddingVerificationClient client;
    @MockitoBean com.FaceLit.backend.realtime.RealtimeWebSocketHandler realtime;
    final UUID owner=UUID.randomUUID();
    @Test void healthProbeWorksWithoutLoginAndDoesNotRunInference() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/health"))
                .andExpect(status().isOk()).andExpect(content().json("{\"status\":\"ok\"}"));
        verifyNoInteractions(client, jwt);
    }
    org.springframework.test.web.servlet.request.RequestPostProcessor as(String role) {
        return authentication(new UsernamePasswordAuthenticationToken(owner,null,List.of(new SimpleGrantedAuthority("ROLE_"+role))));
    }
    String payload(UUID user) throws Exception {
        return json.writeValueAsString(Map.of("user_id",user,"image_base64","synthetic","image_frames",List.of("a","b","c"),"created_by","spoofed"));
    }
    @Test void anonymousAndWrongRolesAreRejected() throws Exception {
        mvc.perform(post("/api/facial/embeddings/from-image").contentType("application/json").content(payload(owner))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/facial/embeddings/from-image").with(as("INSTRUCTOR")).contentType("application/json").content(payload(owner))).andExpect(status().isForbidden());
        verifyNoInteractions(client);
    }
    @Test void cannotEnrollAnotherUser() throws Exception {
        mvc.perform(post("/api/facial/embeddings/from-image").with(as("APPRENTICE")).contentType("application/json").content(payload(UUID.randomUUID()))).andExpect(status().isForbidden());
        verifyNoInteractions(client);
    }
    @Test void ownEnrolmentUsesServerIdentityAndOmitsVector() throws Exception {
        when(client.registerFromImage(any())).thenReturn(json.readTree("{\"id\":\"saved\",\"embedding\":[0.1,0.2],\"created_at\":\"2026-10-26\"}"));
        mvc.perform(post("/api/facial/embeddings/from-image").with(as("APPRENTICE")).contentType("application/json").content(payload(owner)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value("saved")).andExpect(jsonPath("$.embedding").doesNotExist());
        verify(client).registerFromImage(argThat(value -> {
            Map<?,?> body=(Map<?,?>)value;
            return owner.equals(body.get("user_id")) && owner.toString().equals(body.get("created_by"));
        }));
    }
    @Test void internalCredentialFailureDoesNotLogOutUser() throws Exception {
        when(client.registerFromImage(any())).thenThrow(new org.springframework.web.client.HttpClientErrorException(org.springframework.http.HttpStatus.UNAUTHORIZED));
        mvc.perform(post("/api/facial/embeddings/from-image").with(as("APPRENTICE")).contentType("application/json").content(payload(owner))).andExpect(status().isBadGateway());
    }
    @Test void bothCaptureRolesCanValidateLiveness() throws Exception {
        when(client.checkLiveness(any())).thenReturn(json.readTree("{\"live\":true,\"reason\":\"LIVE_OK\"}"));
        for(String role:List.of("APPRENTICE","INSTRUCTOR"))
            mvc.perform(post("/api/facial/embeddings/liveness-check").with(as(role)).contentType("application/json")
                .content("{\"image_frames\":[\"a\",\"b\",\"c\"],\"liveness_challenge\":\"MOVE_UP\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.live").value(true));
    }
    @Test void clientRejectsEmptyOrExampleServerKey() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,()->new FacialEmbeddingVerificationClient("http://localhost:8090",""));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,()->new FacialEmbeddingVerificationClient("http://localhost:8090","change-me"));
    }
    @Test void corsRejectsUnlistedBrowserOrigins() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options("/api/facial/embeddings/from-image")
            .header("Origin","https://untrusted.example.invalid").header("Access-Control-Request-Method","POST"))
            .andExpect(status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options("/api/facial/embeddings/from-image")
            .header("Origin","http://localhost:8081").header("Access-Control-Request-Method","POST"))
            .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin","http://localhost:8081"));
    }
}
