package com.bustransit.dispatch.service;

import com.bustransit.dispatch.dto.AlertPayload;
import com.bustransit.dispatch.dto.GpsPing;
import com.bustransit.dispatch.dto.StopRequestDto;
import com.bustransit.dispatch.entity.Stop;
import com.bustransit.dispatch.repository.StopRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.geo.Distance;
import org.springframework.data.redis.connection.RedisGeoCommands.DistanceUnit;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * These tests mock Redis directly rather than using Testcontainers, so they
 * run fast and need no Docker. See the README for a note on adding a
 * Testcontainers-backed integration test for the real GEODIST behavior.
 */
@ExtendWith(MockitoExtension.class)
class GeofenceServiceTest {

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private GeoOperations<String, String> geoOperations;
    @Mock
    private HashOperations<String, Object, Object> hashOperations;
    @Mock
    private StopRepository stopRepository;
    @Mock
    private AlertPublisher alertPublisher;

    private GeofenceService geofenceService;

    private static final Long STOP_ID = 2L;
    private static final Long ROUTE_ID = 1L;
    private static final String BUS_ID = "bus-42";

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForGeo()).thenReturn(geoOperations);
        lenient().when(redis.opsForHash()).thenReturn(hashOperations);

        geofenceService = new GeofenceService(redis, stopRepository, alertPublisher);
        ReflectionTestUtils.setField(geofenceService, "thresholdMeters", 300.0);
        ReflectionTestUtils.setField(geofenceService, "geoIndexKey", "geo:index");
        ReflectionTestUtils.setField(geofenceService, "pendingKeyPrefix", "pending:");

        lenient().when(stopRepository.findById(STOP_ID))
                .thenReturn(Optional.of(new Stop(ROUTE_ID, "City Hall", 12.9750, 77.5990)));
    }

    @Test
    void firesAlertExactlyOnceWhenBusEntersThreshold() {
        Map<Object, Object> pending = new HashMap<>();
        pending.put(String.valueOf(STOP_ID), "Downtown Mall");
        when(hashOperations.entries("pending:" + ROUTE_ID)).thenReturn(pending);

        // Bus is 150m from the stop - inside the 300m threshold.
        when(geoOperations.distance(eq("geo:index"), eq("bus:" + BUS_ID), eq("stop:" + STOP_ID), eq(DistanceUnit.METERS)))
                .thenReturn(new Distance(150.0, DistanceUnit.METERS));

        GpsPing ping = new GpsPing(BUS_ID, ROUTE_ID, 12.9755, 77.5995, System.currentTimeMillis());
        geofenceService.handleGpsPing(ping);

        // Exactly one alert published, with the correct stop and distance.
        verify(alertPublisher, times(1)).publish(argThat((AlertPayload a) ->
                a.busId().equals(BUS_ID) && a.stopId().equals(STOP_ID) && a.distanceMeters() == 150.0));

        // And the stop must be removed from "pending" so a later ping can't re-fire it.
        verify(hashOperations, times(1)).delete("pending:" + ROUTE_ID, String.valueOf(STOP_ID));
    }

    @Test
    void doesNotFireAlertWhenBusIsOutsideThreshold() {
        Map<Object, Object> pending = new HashMap<>();
        pending.put(String.valueOf(STOP_ID), "Downtown Mall");
        when(hashOperations.entries("pending:" + ROUTE_ID)).thenReturn(pending);

        // Bus is 900m away - outside the 300m threshold.
        when(geoOperations.distance(eq("geo:index"), eq("bus:" + BUS_ID), eq("stop:" + STOP_ID), eq(DistanceUnit.METERS)))
                .thenReturn(new Distance(900.0, DistanceUnit.METERS));

        GpsPing ping = new GpsPing(BUS_ID, ROUTE_ID, 12.9800, 77.6100, System.currentTimeMillis());
        geofenceService.handleGpsPing(ping);

        verify(alertPublisher, never()).publish(any());
        verify(hashOperations, never()).delete(anyString(), any());
    }

    @Test
    void secondPingAfterAlertDoesNotFireAgain() {
        // First ping: stop is pending and in range -> fires once, and the
        // service removes it from "pending". We simulate that removal by
        // returning an empty pending map on the second call, exactly as
        // real Redis would after the HDEL.
        Map<Object, Object> pendingBeforeAlert = new HashMap<>();
        pendingBeforeAlert.put(String.valueOf(STOP_ID), "Downtown Mall");

        when(hashOperations.entries("pending:" + ROUTE_ID))
                .thenReturn(pendingBeforeAlert)
                .thenReturn(new HashMap<>()); // empty after HDEL

        when(geoOperations.distance(eq("geo:index"), eq("bus:" + BUS_ID), eq("stop:" + STOP_ID), eq(DistanceUnit.METERS)))
                .thenReturn(new Distance(100.0, DistanceUnit.METERS));

        GpsPing firstPing = new GpsPing(BUS_ID, ROUTE_ID, 12.9751, 77.5991, System.currentTimeMillis());
        GpsPing secondPing = new GpsPing(BUS_ID, ROUTE_ID, 12.9752, 77.5992, System.currentTimeMillis() + 2000);

        geofenceService.handleGpsPing(firstPing);
        geofenceService.handleGpsPing(secondPing);

        // Still only one publish call across both pings.
        verify(alertPublisher, times(1)).publish(any());
    }

    @Test
    void registeringPendingRequestIndexesStopAndWritesHash() {
        StopRequestDto request = new StopRequestDto(STOP_ID, ROUTE_ID, "Downtown Mall");

        geofenceService.registerPendingRequest(request);

        verify(geoOperations, times(1)).add(eq("geo:index"), any(), eq("stop:" + STOP_ID));
        verify(hashOperations, times(1)).put("pending:" + ROUTE_ID, String.valueOf(STOP_ID), "Downtown Mall");
    }
}
