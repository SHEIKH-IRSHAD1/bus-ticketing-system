package com.bustransit.dispatch.service;

import com.bustransit.dispatch.dto.GpsPing;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Subscribes to "bus/+/gps" on startup. Every incoming GPS ping is parsed
 * and handed to GeofenceService, which does the actual geofence check.
 *
 * Note: this callback runs on Paho's own internal thread, not on a servlet
 * container thread, so calling blocking Redis/JPA code here does not tie up
 * any HTTP request-handling capacity.
 */
@Component
public class GpsMqttListener implements MqttCallbackExtended {

    private static final Logger log = LoggerFactory.getLogger(GpsMqttListener.class);

    private final MqttClient mqttClient;
    private final GeofenceService geofenceService;
    private final ObjectMapper objectMapper;

    @Value("${mqtt.gps-topic-filter}")
    private String gpsTopicFilter;

    public GpsMqttListener(MqttClient mqttClient, GeofenceService geofenceService, ObjectMapper objectMapper) {
        this.mqttClient = mqttClient;
        this.geofenceService = geofenceService;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void subscribe() throws Exception {
        mqttClient.setCallback(this);
        subscribeToGpsTopic();
    }

    /**
     * The client uses cleanSession=true, which means the broker forgets our
     * subscriptions whenever the connection drops. Paho's automatic reconnect
     * restores the TCP connection but NOT the subscription - so without this
     * hook, a broker restart would leave the Dispatch Engine connected but
     * silently deaf to all GPS pings. connectComplete fires on every
     * (re)connect, which is exactly when we need to subscribe again.
     */
    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        if (reconnect) {
            try {
                subscribeToGpsTopic();
                log.info("Re-subscribed to '{}' after reconnecting to {}", gpsTopicFilter, serverURI);
            } catch (Exception e) {
                log.error("Failed to re-subscribe after reconnect: {}", e.getMessage());
            }
        }
    }

    private void subscribeToGpsTopic() throws Exception {
        mqttClient.subscribe(gpsTopicFilter, 1); // QoS 1: at-least-once is enough for frequent GPS pings
        log.info("Subscribed to MQTT topic filter '{}' at {}", gpsTopicFilter, mqttClient.getServerURI());
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        try {
            GpsPing ping = objectMapper.readValue(message.getPayload(), GpsPing.class);
            geofenceService.handleGpsPing(ping);
        } catch (Exception e) {
            // A single malformed message must never take down the listener thread.
            log.warn("Failed to process GPS message on topic '{}': {}", topic, e.getMessage());
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("MQTT connection lost: {}. Paho will attempt automatic reconnect.", cause.getMessage());
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // no-op: we only care about delivery confirmation for published alerts,
        // which AlertPublisher handles synchronously via publish(..).
    }
}
