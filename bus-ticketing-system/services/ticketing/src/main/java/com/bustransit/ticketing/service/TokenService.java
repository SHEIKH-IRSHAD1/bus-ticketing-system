package com.bustransit.ticketing.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * Generates and verifies HMAC-SHA256 signed tokens for QR tickets.
 *
 * Token format: "{ticketId}.{expiryEpochSeconds}.{base64url(signature)}"
 * The signature covers "{ticketId}.{expiryEpochSeconds}" using a secret key
 * known only to this service - so nobody can forge a valid token without it,
 * and we can verify a token's authenticity without a database round trip.
 */
@Service
public class TokenService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    @Value("${ticket.hmac-secret}")
    private String hmacSecret;

    public String generate(Long ticketId, Instant expiry) {
        String payload = ticketId + "." + expiry.getEpochSecond();
        String signature = sign(payload);
        return payload + "." + signature;
    }

    public TokenVerificationResult verify(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return TokenVerificationResult.invalid("MALFORMED_TOKEN");
        }

        String ticketIdPart = parts[0];
        String expiryPart = parts[1];
        String providedSignature = parts[2];
        String payload = ticketIdPart + "." + expiryPart;

        String expectedSignature = sign(payload);
        if (!constantTimeEquals(expectedSignature, providedSignature)) {
            return TokenVerificationResult.invalid("INVALID_SIGNATURE");
        }

        long ticketId;
        long expiryEpochSeconds;
        try {
            ticketId = Long.parseLong(ticketIdPart);
            expiryEpochSeconds = Long.parseLong(expiryPart);
        } catch (NumberFormatException e) {
            return TokenVerificationResult.invalid("MALFORMED_TOKEN");
        }

        if (Instant.now().getEpochSecond() > expiryEpochSeconds) {
            return TokenVerificationResult.invalid("EXPIRED");
        }

        return TokenVerificationResult.valid(ticketId);
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(hmacSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] rawSignature = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(rawSignature);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign token payload", e);
        }
    }

    /** Avoids timing attacks that could otherwise leak the signature byte by byte. */
    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    public record TokenVerificationResult(boolean valid, String reason, Long ticketId) {
        static TokenVerificationResult valid(long ticketId) {
            return new TokenVerificationResult(true, "OK", ticketId);
        }

        static TokenVerificationResult invalid(String reason) {
            return new TokenVerificationResult(false, reason, null);
        }
    }
}
