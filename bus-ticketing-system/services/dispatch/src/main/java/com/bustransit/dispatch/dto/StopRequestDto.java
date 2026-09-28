package com.bustransit.dispatch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request body the Kiosk Node POSTs to /stops/request.
 */
public record StopRequestDto(
        @NotNull Long stopId,
        @NotNull Long routeId,
        @NotBlank String destination
) {
}
