package com.bustransit.dispatch.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Payload a bus publishes to MQTT topic "bus/{busId}/gps".
 * routeId is included so the Dispatch Engine knows which route's pending
 * stop requests to check this ping against, without a separate lookup.
 */
public record GpsPing(
        @JsonProperty("busId") String busId,
        @JsonProperty("routeId") Long routeId,
        @JsonProperty("lat") double lat,
        @JsonProperty("lon") double lon,
        @JsonProperty("timestamp") long timestamp
) {
    @JsonCreator
    public GpsPing {
        // canonical constructor - Jackson uses this via the @JsonProperty-annotated fields
    }
}
