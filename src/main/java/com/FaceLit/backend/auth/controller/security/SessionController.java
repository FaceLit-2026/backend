package com.FaceLit.backend.auth.controller.security;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.FaceLit.backend.auth.service.roleandpermission.JwtService;

@RestController
@RequestMapping("/api/auth")
public class SessionController {
    private final JwtService jwt;
    public SessionController(JwtService jwt) { this.jwt = jwt; }
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String authorization) {
        jwt.revokeToken(authorization.substring(7));
        return ResponseEntity.noContent().build();
    }
}
