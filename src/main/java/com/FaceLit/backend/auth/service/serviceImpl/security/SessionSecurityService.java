package com.FaceLit.backend.auth.service.serviceImpl.security;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.FaceLit.backend.auth.model.enums.*;
import com.FaceLit.backend.auth.repository.security.UserSessionRepository;
import com.FaceLit.backend.auth.repository.roleandpermission.UserRoleRepository;

@Service
public class SessionSecurityService {
    private final UserSessionRepository sessions;
    private final UserRoleRepository roles;
    public SessionSecurityService(UserSessionRepository sessions, UserRoleRepository roles) {
        this.sessions = sessions; this.roles = roles;
    }
    @Transactional(readOnly = true)
    public boolean isActive(UUID sessionId, UUID userId, String role) {
        return sessions.findById(sessionId).filter(s -> s.getSessionStatus() == SessionStatus.ACTIVE
                && s.getEndDate() == null && s.getDeletedAt() == null
                && s.getUser().getIdUser().equals(userId)
                && s.getUser().getDeletedAt() == null
                && s.getUser().getAccountStatus() == AccountStatus.ACTIVE
                && s.getUser().getCredential() != null
                && s.getUser().getCredential().getCredentialStatus() == CredentialStatus.ACTIVE)
                .filter(s -> roles.findByUserId(userId)
                    .map(r -> r.getRole().getNameRole().name().equals(role)).orElse(false)).isPresent();
    }
    @Transactional
    public void revokeAll(UUID userId) { sessions.revokeAll(userId, OffsetDateTime.now()); }
    @Transactional
    public void close(UUID sessionId, UUID userId) {
        sessions.findById(sessionId).filter(s -> s.getUser().getIdUser().equals(userId)).ifPresent(s -> {
            s.setSessionStatus(SessionStatus.INACTIVE); s.setEndDate(OffsetDateTime.now()); sessions.save(s);
        });
    }
}
