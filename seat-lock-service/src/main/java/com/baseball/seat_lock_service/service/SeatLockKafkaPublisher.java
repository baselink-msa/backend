package com.baseball.seat_lock_service.service;

import com.baseball.seat_lock_service.event.SeatLockEventEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SeatLockKafkaPublisher {

    private final boolean enabled;
    private final String topicName;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Counter successCounter;
    private final Counter failureCounter;

    public SeatLockKafkaPublisher(
            ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            @Value("${app.kafka.enabled:false}") boolean enabled,
            @Value("${app.kafka.topics.reservation-lifecycle-events:reservation.lifecycle.events}") String topicName) {
        this.enabled = enabled;
        this.topicName = topicName;
        this.kafkaTemplate = kafkaTemplateProvider.getIfAvailable();
        this.objectMapper = objectMapper;
        this.successCounter = Counter.builder("seat_lock_kafka_publish_total")
                .tag("topic", topicName)
                .tag("result", "success")
                .register(meterRegistry);
        this.failureCounter = Counter.builder("seat_lock_kafka_publish_total")
                .tag("topic", topicName)
                .tag("result", "failure")
                .register(meterRegistry);
    }

    public void publish(SeatLockEventEnvelope event) {
        if (!enabled) {
            return;
        }

        if (kafkaTemplate == null) {
            failureCounter.increment();
            log.warn("Kafka is enabled but KafkaTemplate is missing: eventType={}, eventId={}, topic={}",
                    event.eventType(), event.eventId(), topicName);
            return;
        }

        try {
            String key = resolvePartitionKey(event);
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(topicName, key, payload)
                    .whenComplete((result, throwable) -> {
                        if (throwable == null) {
                            successCounter.increment();
                            log.debug("Seat lock event Kafka publish completed: eventType={}, eventId={}, topic={}, key={}",
                                    event.eventType(), event.eventId(), topicName, key);
                        } else {
                            failureCounter.increment();
                            log.warn("Seat lock event Kafka publish failed: eventType={}, eventId={}, topic={}, key={}",
                                    event.eventType(), event.eventId(), topicName, key, throwable);
                        }
                    });
        } catch (Exception e) {
            failureCounter.increment();
            log.warn("Seat lock event Kafka publish request failed: eventType={}, eventId={}, topic={}",
                    event.eventType(), event.eventId(), topicName, e);
        }
    }

    private String resolvePartitionKey(SeatLockEventEnvelope event) {
        if (event.aggregateId() != null && !event.aggregateId().isBlank()) {
            return event.aggregateId();
        }
        if (event.gameId() != null) {
            return event.gameId().toString();
        }
        return event.eventId().toString();
    }
}
