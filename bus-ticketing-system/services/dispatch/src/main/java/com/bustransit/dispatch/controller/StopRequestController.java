package com.bustransit.dispatch.controller;

import com.bustransit.dispatch.dto.StopRequestDto;
import com.bustransit.dispatch.service.GeofenceService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/stops")
public class StopRequestController {

    private final GeofenceService geofenceService;

    public StopRequestController(GeofenceService geofenceService) {
        this.geofenceService = geofenceService;
    }

    /**
     * Called by the Kiosk Node when a passenger requests a pickup.
     * Routed here from the API Gateway at /api/stops/request.
     */
    @PostMapping("/request")
    public ResponseEntity<Map<String, String>> requestStop(@Valid @RequestBody StopRequestDto request) {
        geofenceService.registerPendingRequest(request);
        return ResponseEntity.accepted().body(Map.of("status", "registered"));
    }
}
