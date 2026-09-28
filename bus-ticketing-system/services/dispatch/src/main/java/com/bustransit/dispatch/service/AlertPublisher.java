package com.bustransit.dispatch.service;

import com.bustransit.dispatch.dto.AlertPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AlertPublisher {

    private static final Logger log = LoggerFactory.getLogger(AlertPublisher.class);

    private final MqttClient mqttClient;
    private final ObjectMapper objectMapper;

    @Value("${mqtt.alert-topic-template}")
    private String alertTopicTemplate;

    public AlertPublisher(MqttClient mqttClient, ObjectMapper objectMapper) {
        this.mqttClient = mqttClient;
        this.objectMapper = objectMapper;
    }

    public void publish(AlertPayload alert) {
        String topic = String.format(alertTopicTemplate, alert.busId());
        try {
            byte[] payload = objectMapper.writeValueAsBytes(alert);
            MqttMessage message = new MqttMessage(payload);
            message.setQos(1);
            mqttClient.publish(topic, message);
            log.info("Published alert to '{}': stop={} distance={}m", topic, alert.stopId(), alert.distanceMeters());
        } catch (Exception e) {
            log.error("Failed to publish alert to '{}': {}", topic, e.getMessage());
        }
    }
}
