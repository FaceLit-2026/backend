package com.FaceLit.backend.auth.service.security;

import com.FaceLit.backend.auth.model.security.User;
import com.FaceLit.backend.auth.model.security.UserSession;

public interface UserSessionService {

    // Registra una nueva sesión cuando el usuario hace login exitoso
    UserSession registerSession(User user);

    // Cierra la sesión cuando el usuario hace logout — llena endDate
    void closeSession(UserSession session);
}
