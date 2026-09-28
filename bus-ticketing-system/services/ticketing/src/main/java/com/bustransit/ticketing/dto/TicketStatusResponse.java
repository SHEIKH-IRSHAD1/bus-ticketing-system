package com.bustransit.ticketing.dto;

public record TicketStatusResponse(
        Long ticketId,
        String status,
        String qrToken
) {
}
