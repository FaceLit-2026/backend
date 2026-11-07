package com.FaceLit.backend.auth.service.security;

import com.FaceLit.backend.auth.model.enums.RoleName;
import com.FaceLit.backend.auth.model.security.Credential;
import com.FaceLit.backend.auth.model.security.User;

public interface InitialCredentialService {

    String generateInitialPassword();

    void encodeGeneratedPassword(Credential credential, String rawPassword);

    void sendWelcomeAfterCommit(User user, Credential credential, RoleName role, String rawPassword,
            String fichaCode, String programName);

    void requestEmailChange(User user, Credential credential, String email);

    void retryWelcome(User user);
}
