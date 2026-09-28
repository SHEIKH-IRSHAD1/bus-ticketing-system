package com.bustransit.ticketing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PurchaseRequest(
        @NotNull Long routeId,
        @NotBlank String passengerId
) {
}
