package com.FaceLit.backend.auth.service.serviceImpl.security;

import java.security.SecureRandom;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.FaceLit.backend.auth.model.enums.RoleName;
import com.FaceLit.backend.auth.model.security.Credential;
import com.FaceLit.backend.auth.model.security.User;
import com.FaceLit.backend.auth.service.security.InitialCredentialService;

@Service
public class InitialCredentialServiceImpl implements InitialCredentialService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789@$!%*?";
    private static final String SYMBOLS = "@$!%*?";

    private final PasswordEncoder passwordEncoder;
    private final AccountAccessService accountAccess;

    public InitialCredentialServiceImpl(PasswordEncoder passwordEncoder, AccountAccessService accountAccess) {
        this.passwordEncoder = passwordEncoder;
        this.accountAccess = accountAccess;
    }

    @Override
    public String generateInitialPassword() {
        return newGeneratedPassword();
    }

    static String newGeneratedPassword() {
        StringBuilder password = new StringBuilder();
        password.append((char) ('A' + RANDOM.nextInt(26)));
        password.append(RANDOM.nextInt(10));
        password.append(SYMBOLS.charAt(RANDOM.nextInt(SYMBOLS.length())));
        while (password.length() < 12) {
            password.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return password.toString();
    }

    @Override
    public void encodeGeneratedPassword(Credential credential, String rawPassword) {
        credential.setPassword(passwordEncoder.encode(rawPassword));
    }

    @Override
    public void sendWelcomeAfterCommit(User user, Credential credential, RoleName role, String rawPassword,
            String fichaCode, String programName) {
        accountAccess.welcome(user, credential, rawPassword);
    }

    @Override
    public void requestEmailChange(User user, Credential credential, String email) {
        accountAccess.changeEmail(user, credential, email);
    }

    @Override
    public void retryWelcome(User user) { accountAccess.retryWelcome(user); }
}
