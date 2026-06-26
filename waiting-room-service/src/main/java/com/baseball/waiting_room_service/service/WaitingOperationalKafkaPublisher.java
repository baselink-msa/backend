package com.baseball.waiting_room_service.service;

import com.baseball.waiting_room_service.event.WaitingEventEnvelope;
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
public class WaitingOperationalKafkaPublisher {

    private final boolean enabled;
    private final String topicName;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Counter successCounter;
    private final Counter failureCounter;

    public WaitingOperationalKafkaPublisher(
            ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            @Value("${app.kafka.enabled:false}") boolean enabled,
            @Value("${app.kafka.topics.waiting-operational-events:waiting.operational.events}") String topicName) {
        this.enabled = enabled;
        this.topicName = topicName;
        this.kafkaTemplate = kafkaTemplateProvider.getIfAvailable();
        this.objectMapper = objectMapper;
        this.successCounter = Counter.builder("waiting_kafka_publish_total")
                .tag("topic", topicName)
                .tag("result", "success")
                .register(meterRegistry);
        this.failureCounter = Counter.builder("waiting_kafka_publish_total")
                .tag("topic", topicName)
                .tag("result", "failure")
                .register(meterRegistry);
    }

    public void publish(WaitingEventEnvelope event) {
        if (!enabled) {
            return;
        }

        if (kafkaTemplate == null) {
            failureCounter.increment();
            log.warn("Kafka 발행이 활성화되어 있지만 KafkaTemplate이 없습니다: eventType={}, eventId={}, topic={}",
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
                            log.debug("Waiting event Kafka 발행 완료: eventType={}, eventId={}, topic={}, key={}",
                                    event.eventType(), event.eventId(), topicName, key);
                        } else {
                            failureCounter.increment();
                            log.warn("Waiting event Kafka 발행 실패: eventType={}, eventId={}, topic={}, key={}",
                                    event.eventType(), event.eventId(), topicName, key, throwable);
                        }
                    });
        } catch (Exception e) {
            failureCounter.increment();
            log.warn("Waiting event Kafka 발행 요청 생성 실패: eventType={}, eventId={}, topic={}",
                    event.eventType(), event.eventId(), topicName, e);
        }
    }

    private String resolvePartitionKey(WaitingEventEnvelope event) {
        if (event.gameId() != null) {
            return event.gameId().toString();
        }
        if (event.aggregateId() != null && !event.aggregateId().isBlank()) {
            return event.aggregateId();
        }
        return event.eventId().toString();
    }
}
