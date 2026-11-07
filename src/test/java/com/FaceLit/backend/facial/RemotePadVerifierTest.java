package com.FaceLit.backend.facial;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class RemotePadVerifierTest {
    @Test void missingConfigurationCannotAcceptBrowserScores() {
        var verifier = new RemotePadVerifier("", "");
        assertFalse(verifier.available());
        assertThrows(ResponseStatusException.class, () -> verifier.requireReal(UUID.randomUUID(), null, null));
        assertFalse(new RemotePadVerifier("http://pad.example/verify", "secret").available());
    }

    @Test void requiresRealAndEveryIndependentCheckBoundToUserAndChallenge() {
        UUID user = UUID.randomUUID(), challenge = UUID.randomUUID();
        assertFalse(RemotePadVerifier.accepts(null, user, challenge));
        for (String label : new String[] { "REAL", "PHOTO", "VIDEO", "SCREEN", "PRINT", "UNKNOWN", "real" }) {
            for (int mask = 0; mask < 64; mask++) {
                var verdict = new RemotePadVerifier.Verdict(user, challenge, label, (mask & 1) != 0,
                    (mask & 2) != 0, (mask & 4) != 0, (mask & 8) != 0, (mask & 16) != 0, (mask & 32) != 0);
                assertEquals(label.equals("REAL") && mask == 63, RemotePadVerifier.accepts(verdict, user, challenge));
                assertFalse(RemotePadVerifier.accepts(verdict, UUID.randomUUID(), challenge));
                assertFalse(RemotePadVerifier.accepts(verdict, user, UUID.randomUUID()));
            }
        }
    }
}
