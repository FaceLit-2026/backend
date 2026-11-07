package com.FaceLit.backend.auth.service.security;

import java.util.UUID;

/** The password lives only in the in-memory after-commit event, never in the delivery table. */
public record InitialCredentialsEmailRequested(UUID requestId, String rawPassword) {
    public InitialCredentialsEmailRequested(UUID requestId) { this(requestId, null); }
    @Override public String toString() { return "InitialCredentialsEmailRequested[requestId=" + requestId + "]"; }
}
