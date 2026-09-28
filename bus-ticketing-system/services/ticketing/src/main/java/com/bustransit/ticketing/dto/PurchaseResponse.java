package com.bustransit.ticketing.dto;

public record PurchaseResponse(
        Long ticketId,
        String checkoutUrl
) {
}
