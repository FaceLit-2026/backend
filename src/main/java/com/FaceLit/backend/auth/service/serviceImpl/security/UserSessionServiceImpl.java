package com.FaceLit.backend.auth.service.serviceImpl.security;

import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;

import com.FaceLit.backend.auth.model.security.User;
import com.FaceLit.backend.auth.model.security.UserSession;
import com.FaceLit.backend.auth.model.enums.SessionStatus;
import com.FaceLit.backend.auth.repository.security.UserSessionRepository;
import com.FaceLit.backend.auth.service.security.UserSessionService;

@Service
public class UserSessionServiceImpl implements UserSessionService {

    private final UserSessionRepository userSessionRepository;

    public UserSessionServiceImpl(UserSessionRepository userSessionRepository) {
        this.userSessionRepository = userSessionRepository;

    }

    @Override
    public UserSession registerSession(User user) {
        // Crea y guarda una nueva sesión cada vez que el usuario hace login
        // Un usuario puede tener muchas sesiones — una por cada login exitoso
        UserSession session = new UserSession();
        session.setUser(user);
        session.setStartDate(OffsetDateTime.now());
        session.setSessionStatus(SessionStatus.ACTIVE);
        // endDate queda null — se llena cuando el usuario cierre sesión
        return userSessionRepository.save(session);
    }

    @Override
    public void closeSession(UserSession session) {
        // Cierra la sesión — se usará cuando implementes el logout
        session.setEndDate(OffsetDateTime.now());
        session.setSessionStatus(SessionStatus.INACTIVE);
        userSessionRepository.save(session);
    }

}
