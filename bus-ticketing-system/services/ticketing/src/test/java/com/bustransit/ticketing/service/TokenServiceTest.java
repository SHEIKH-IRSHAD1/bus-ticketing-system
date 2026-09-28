package com.bustransit.ticketing.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

class TokenServiceTest {

    private final TokenService tokenService = new TokenService();

    {
        ReflectionTestUtils.setField(tokenService, "hmacSecret", "unit_test_secret_key_do_not_use_in_prod");
    }

    @Test
    void generatedTokenRoundTripsSuccessfully() {
        Instant expiry = Instant.now().plus(90, ChronoUnit.MINUTES);
        String token = tokenService.generate(42L, expiry);

        TokenService.TokenVerificationResult result = tokenService.verify(token);

        assertTrue(result.valid());
        assertEquals(42L, result.ticketId());
    }

    @Test
    void expiredTokenIsRejected() {
        Instant expiry = Instant.now().minus(1, ChronoUnit.MINUTES); // already expired
        String token = tokenService.generate(42L, expiry);

        TokenService.TokenVerificationResult result = tokenService.verify(token);

        assertFalse(result.valid());
        assertEquals("EXPIRED", result.reason());
    }

    @Test
    void tamperedSignatureIsRejected() {
        Instant expiry = Instant.now().plus(90, ChronoUnit.MINUTES);
        String token = tokenService.generate(42L, expiry);

        // Flip the ticket ID in the payload without re-signing - simulates forgery.
        String[] parts = token.split("\\.");
        String tampered = "99999." + parts[1] + "." + parts[2];

        TokenService.TokenVerificationResult result = tokenService.verify(tampered);

        assertFalse(result.valid());
        assertEquals("INVALID_SIGNATURE", result.reason());
    }

    @Test
    void malformedTokenIsRejected() {
        TokenService.TokenVerificationResult result = tokenService.verify("not-a-valid-token");

        assertFalse(result.valid());
        assertEquals("MALFORMED_TOKEN", result.reason());
    }
}
