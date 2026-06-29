package com.baseball.waiting_room_service.service;

import com.baseball.waiting_room_service.event.CapacitySignalEventEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class CapacitySignalKafkaPublisher {

    private static final String NORMAL = "NORMAL";
    private static final String STOP = "STOP";

    private final boolean enabled;
    private final String topicName;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Counter successCounter;
    private final Counter failureCounter;
    private final Map<Long, String> lastPublishedPressureLevelByGame = new ConcurrentHashMap<>();

    @Autowired
    public CapacitySignalKafkaPublisher(
            ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            @Value("${app.kafka.enabled:false}") boolean enabled,
            @Value("${app.kafka.topics.capacity-signals:capacity.signals}") String topicName) {
        this(kafkaTemplateProvider.getIfAvailable(), objectMapper, meterRegistry, enabled, topicName);
    }

    CapacitySignalKafkaPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            boolean enabled,
            String topicName) {
        this.enabled = enabled;
        this.topicName = topicName;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.successCounter = Counter.builder("capacity_signal_kafka_publish_total")
                .tag("topic", topicName)
                .tag("result", "success")
                .register(meterRegistry);
        this.failureCounter = Counter.builder("capacity_signal_kafka_publish_total")
                .tag("topic", topicName)
                .tag("result", "failure")
                .register(meterRegistry);
    }

    public void recordAdmissionDecision(
            Long gameId,
            String dbPressureLevel,
            int currentDbConnections,
            int dbConnectionBudget,
            int dbThrottlePercent,
            int policyMaxEnterPerMinute,
            int currentReadyPodCount,
            int projectedReadyPodCount,
            int baseEnterPerMinute,
            int effectiveEnterPerMinute,
            int projectedEnterPerMinute,
            long currentMinuteRemainingSlots,
            boolean canEnter) {
        if (!enabled) {
            return;
        }

        String normalizedLevel = normalizeLevel(dbPressureLevel);
        Long stateKey = gameId == null ? -1L : gameId;
        String previousLevel = lastPublishedPressureLevelByGame.put(stateKey, normalizedLevel);
        String eventType = resolveEventType(previousLevel, normalizedLevel);
        if (eventType == null) {
            return;
        }

        CapacitySignalEventEnvelope event = CapacitySignalEventEnvelope.create(
                eventType,
                gameId,
                Map.ofEntries(
                        Map.entry("reason", "RDS_CONNECTION_PRESSURE"),
                        Map.entry("dbPressureLevel", normalizedLevel),
                        Map.entry("currentDbConnections", currentDbConnections),
                        Map.entry("dbConnectionBudget", dbConnectionBudget),
                        Map.entry("dbThrottlePercent", dbThrottlePercent),
                        Map.entry("policyMaxEnterPerMinute", policyMaxEnterPerMinute),
                        Map.entry("currentReadyPodCount", currentReadyPodCount),
                        Map.entry("projectedReadyPodCount", projectedReadyPodCount),
                        Map.entry("baseEnterPerMinute", baseEnterPerMinute),
                        Map.entry("effectiveEnterPerMinute", effectiveEnterPerMinute),
                        Map.entry("projectedEnterPerMinute", projectedEnterPerMinute),
                        Map.entry("currentMinuteRemainingSlots", currentMinuteRemainingSlots),
                        Map.entry("canEnter", canEnter)));
        publish(event);
    }

    private String resolveEventType(String previousLevel, String currentLevel) {
        if (NORMAL.equals(currentLevel)) {
            if (previousLevel == null || NORMAL.equals(previousLevel)) {
                return null;
            }
            return "ADMISSION_THROTTLE_RECOVERED";
        }

        if (currentLevel.equals(previousLevel)) {
            return null;
        }

        if (STOP.equals(currentLevel)) {
            return "ADMISSION_STOP_APPLIED";
        }
        return "ADMISSION_THROTTLE_APPLIED";
    }

    private void publish(CapacitySignalEventEnvelope event) {
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
                            log.info("Capacity signal Kafka 발행 완료: eventType={}, eventId={}, topic={}, key={}",
                                    event.eventType(), event.eventId(), topicName, key);
                        } else {
                            failureCounter.increment();
                            log.warn("Capacity signal Kafka 발행 실패: eventType={}, eventId={}, topic={}, key={}",
                                    event.eventType(), event.eventId(), topicName, key, throwable);
                        }
                    });
        } catch (Exception e) {
            failureCounter.increment();
            log.warn("Capacity signal Kafka 발행 요청 생성 실패: eventType={}, eventId={}, topic={}",
                    event.eventType(), event.eventId(), topicName, e);
        }
    }

    private String resolvePartitionKey(CapacitySignalEventEnvelope event) {
        if (event.gameId() != null) {
            return event.gameId().toString();
        }
        if (event.aggregateId() != null && !event.aggregateId().isBlank()) {
            return event.aggregateId();
        }
        return event.eventId().toString();
    }

    private String normalizeLevel(String dbPressureLevel) {
        if (dbPressureLevel == null || dbPressureLevel.isBlank()) {
            return "UNKNOWN";
        }
        return dbPressureLevel.trim().toUpperCase();
    }
}
