package com.FaceLit.backend.facial;

import java.util.UUID;

/** Implementations must independently evaluate capture evidence, never client scores. */
public interface PadVerifier {
    boolean available();
    void requireReal(UUID user, FacialEnrollmentController.Challenge challenge,
                     FacialEnrollmentController.Enrollment enrollment);
}
