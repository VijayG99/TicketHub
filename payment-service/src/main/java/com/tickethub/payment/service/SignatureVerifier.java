package com.tickethub.payment.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** HMAC-SHA256 signature check for provider webhooks (ST-23). Header format: sha256=&lt;hex&gt;. */
@Component
public class SignatureVerifier {

    private final byte[] secret;

    public SignatureVerifier(@Value("${payment.webhook.secret}") String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("PAYMENT_WEBHOOK_SECRET must be configured");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public boolean verify(String rawBody, String header) {
        if (rawBody == null || header == null || header.isBlank()) {
            return false;
        }
        String provided = header.trim();
        if (provided.startsWith("sha256=")) {
            provided = provided.substring("sha256=".length());
        }
        byte[] expected = sign(rawBody).getBytes(StandardCharsets.UTF_8);
        byte[] actual = provided.toLowerCase().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual); // constant-time comparison
    }

    public String sign(String rawBody) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to compute HMAC", e);
        }
    }
}
