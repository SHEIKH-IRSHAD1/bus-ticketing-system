package com.bustransit.dispatch.service;

import com.bustransit.dispatch.dto.AlertPayload;
import com.bustransit.dispatch.dto.GpsPing;
import com.bustransit.dispatch.dto.StopRequestDto;
import com.bustransit.dispatch.entity.Stop;
import com.bustransit.dispatch.repository.StopRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands.DistanceUnit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * Core business logic of the whole system.
 *
 * Data model in Redis:
 *   - ONE geo set "geo:index" holds every bus's current position AND every
 *     indexed stop's position, as members "bus:{busId}" / "stop:{stopId}".
 *     Keeping both in the same geo set lets us use GEODIST directly between
 *     a bus and a stop, instead of computing distances by hand.
 *   - ONE hash per route, "pending:{routeId}", mapping stopId -> destination,
 *     for stops that currently have an un-served passenger request.
 *
 * On every GPS ping: update the bus's position, then check its distance to
 * every pending stop on its route. Crossing the threshold fires exactly one
 * alert (the stop is removed from "pending" immediately after alerting, so
 * a bus idling near a stop doesn't spam duplicate alerts on every later ping).
 */
@Service
public class GeofenceService {

    private static final Logger log = LoggerFactory.getLogger(GeofenceService.class);

    private final StringRedisTemplate redis;
    private final StopRepository stopRepository;
    private final AlertPublisher alertPublisher;

    @Value("${dispatch.geofence.threshold-meters}")
    private double thresholdMeters;

    @Value("${dispatch.redis.geo-index-key}")
    private String geoIndexKey;

    @Value("${dispatch.redis.pending-requests-key-prefix}")
    private String pendingKeyPrefix;

    public GeofenceService(StringRedisTemplate redis, StopRepository stopRepository, AlertPublisher alertPublisher) {
        this.redis = redis;
        this.stopRepository = stopRepository;
        this.alertPublisher = alertPublisher;
    }

    /** Called by the /stops/request endpoint when a passenger requests a pickup. */
    public void registerPendingRequest(StopRequestDto request) {
        ensureStopIndexed(request.stopId());
        String pendingKey = pendingKey(request.routeId());
        redis.opsForHash().put(pendingKey, String.valueOf(request.stopId()), request.destination());
        log.info("Registered pending stop request: route={} stop={} destination={}",
                request.routeId(), request.stopId(), request.destination());
    }

    /** Called for every GPS ping received over MQTT. */
    public void handleGpsPing(GpsPing ping) {
        String busMember = busMember(ping.busId());
        redis.opsForGeo().add(geoIndexKey, new Point(ping.lon(), ping.lat()), busMember);

        String pendingKey = pendingKey(ping.routeId());
        Map<Object, Object> pending = redis.opsForHash().entries(pendingKey);
        if (pending.isEmpty()) {
            return; // nothing waiting on this route - nothing to check
        }

        for (Map.Entry<Object, Object> entry : pending.entrySet()) {
            Long stopId = Long.valueOf(entry.getKey().toString());
            String destination = entry.getValue().toString();
            checkStopAndMaybeAlert(ping.busId(), busMember, stopId, destination, pendingKey);
        }
    }

    private void checkStopAndMaybeAlert(String busId, String busMember, Long stopId, String destination, String pendingKey) {
        String stopMember = stopMember(stopId);

        Distance distance = redis.opsForGeo().distance(geoIndexKey, busMember, stopMember, DistanceUnit.METERS);
        if (distance == null) {
            // Stop wasn't in the geo index (e.g. Redis was flushed) - index it and retry once.
            ensureStopIndexed(stopId);
            distance = redis.opsForGeo().distance(geoIndexKey, busMember, stopMember, DistanceUnit.METERS);
            if (distance == null) {
                log.warn("Could not compute distance for bus={} stop={}; skipping this check.", busId, stopId);
                return;
            }
        }

        if (distance.getValue() <= thresholdMeters) {
            String stopName = stopRepository.findById(stopId).map(Stop::getName).orElse("Unknown stop");
            alertPublisher.publish(new AlertPayload(busId, stopId, stopName, destination, distance.getValue()));

            // Remove from pending immediately: the request has now been served,
            // and this is what prevents firing the same alert again on the next ping.
            redis.opsForHash().delete(pendingKey, String.valueOf(stopId));
        }
    }

    /** Adds a stop's static coordinates to the shared geo index, looking them up from Postgres. */
    private void ensureStopIndexed(Long stopId) {
        Optional<Stop> stop = stopRepository.findById(stopId);
        if (stop.isEmpty()) {
            log.warn("Attempted to index unknown stop id={}", stopId);
            return;
        }
        redis.opsForGeo().add(geoIndexKey, new Point(stop.get().getLon(), stop.get().getLat()), stopMember(stopId));
    }

    private String pendingKey(Long routeId) {
        return pendingKeyPrefix + routeId;
    }

    private String busMember(String busId) {
        return "bus:" + busId;
    }

    private String stopMember(Long stopId) {
        return "stop:" + stopId;
    }
}
