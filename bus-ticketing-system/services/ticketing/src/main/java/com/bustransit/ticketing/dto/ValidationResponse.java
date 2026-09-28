package com.bustransit.ticketing.dto;

public record ValidationResponse(
        boolean valid,
        String reason,
        Long ticketId
) {
    public static ValidationResponse ok(Long ticketId) {
        return new ValidationResponse(true, "OK", ticketId);
    }

    public static ValidationResponse rejected(String reason) {
        return new ValidationResponse(false, reason, null);
    }
}
