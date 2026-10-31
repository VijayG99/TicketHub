package com.tickethub.payment.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SignatureVerifierTest {

    private final SignatureVerifier verifier = new SignatureVerifier("test-secret");

    @Test
    void acceptsCorrectSignatureWithAndWithoutPrefix() {
        String body = "{\"eventId\":\"e1\"}";
        String sig = verifier.sign(body);
        assertTrue(verifier.verify(body, "sha256=" + sig));
        assertTrue(verifier.verify(body, sig));
    }

    @Test
    void rejectsTamperedBodyMissingAndWrongSignature() {
        String body = "{\"eventId\":\"e1\"}";
        String sig = verifier.sign(body);
        assertFalse(verifier.verify("{\"eventId\":\"e2\"}", sig));
        assertFalse(verifier.verify(body, null));
        assertFalse(verifier.verify(body, ""));
        assertFalse(verifier.verify(body, "sha256=deadbeef"));
    }

    @Test
    void refusesToStartWithoutSecret() {
        assertThrows(IllegalStateException.class, () -> new SignatureVerifier(" "));
    }
}
