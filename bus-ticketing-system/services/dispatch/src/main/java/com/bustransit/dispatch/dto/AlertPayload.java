package com.bustransit.dispatch.dto;

/**
 * Payload published to MQTT topic "alerts/{busId}/stop" when a bus crosses
 * the geofence threshold for a stop that has a pending passenger request.
 */
public record AlertPayload(
        String busId,
        Long stopId,
        String stopName,
        String destination,
        double distanceMeters
) {
}
