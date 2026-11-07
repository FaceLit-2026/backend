package com.FaceLit.backend.auth.exception;

/** Expected recovery rejection; contains no code, credential or token. */
public class RecoverySecurityException extends RuntimeException {
    private final int status;
    private final long retryAfter;
    public RecoverySecurityException(int status, String message) { this(status, message, 0); }
    public RecoverySecurityException(int status, String message, long retryAfter) {
        super(message); this.status = status; this.retryAfter = retryAfter;
    }
    public int getStatus() { return status; }
    public long getRetryAfter() { return retryAfter; }
}
