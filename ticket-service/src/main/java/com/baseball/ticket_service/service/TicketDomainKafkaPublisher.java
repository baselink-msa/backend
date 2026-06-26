package com.baseball.ticket_service.service;

import com.fasterxml.jackson.databind.JsonNode;
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
public class TicketDomainKafkaPublisher {

    private final boolean enabled;
    private final String topicName;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Counter successCounter;
    private final Counter failureCounter;

    public TicketDomainKafkaPublisher(
            ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            @Value("${app.kafka.enabled:false}") boolean enabled,
            @Value("${app.kafka.topics.ticket-domain-events:ticket.domain.events}") String topicName) {
        this.enabled = enabled;
        this.topicName = topicName;
        this.kafkaTemplate = kafkaTemplateProvider.getIfAvailable();
        this.objectMapper = objectMapper;
        this.successCounter = Counter.builder("ticket_kafka_publish_total")
                .tag("topic", topicName)
                .tag("result", "success")
                .register(meterRegistry);
        this.failureCounter = Counter.builder("ticket_kafka_publish_total")
                .tag("topic", topicName)
                .tag("result", "failure")
                .register(meterRegistry);
    }

    public void publishDomainEvent(ClaimedOutboxEvent event) {
        if (!enabled) {
            return;
        }

        if (kafkaTemplate == null) {
            failureCounter.increment();
            log.warn(
                    "Kafka 발행이 활성화되어 있지만 KafkaTemplate이 없습니다: outboxId={}, topic={}",
                    event.outboxId(),
                    topicName);
            return;
        }

        try {
            String key = resolvePartitionKey(event);
            kafkaTemplate.send(topicName, key, event.payload())
                    .whenComplete((result, throwable) -> {
                        if (throwable == null) {
                            successCounter.increment();
                            log.debug(
                                    "Ticket domain event Kafka 발행 완료: outboxId={}, topic={}, key={}",
                                    event.outboxId(),
                                    topicName,
                                    key);
                        } else {
                            failureCounter.increment();
                            log.warn(
                                    "Ticket domain event Kafka 발행 실패: outboxId={}, topic={}, key={}",
                                    event.outboxId(),
                                    topicName,
                                    key,
                                    throwable);
                        }
                    });
        } catch (Exception e) {
            failureCounter.increment();
            log.warn(
                    "Ticket domain event Kafka 발행 요청 생성 실패: outboxId={}, topic={}",
                    event.outboxId(),
                    topicName,
                    e);
        }
    }

    private String resolvePartitionKey(ClaimedOutboxEvent event) {
        try {
            JsonNode root = objectMapper.readTree(event.payload());
            if (root.hasNonNull("gameId")) {
                return root.get("gameId").asText();
            }
            if (root.hasNonNull("aggregateId")) {
                return root.get("aggregateId").asText();
            }
            if (root.hasNonNull("eventId")) {
                return root.get("eventId").asText();
            }
        } catch (Exception e) {
            log.debug("Kafka partition key 파싱 실패, outboxId를 key로 사용합니다: outboxId={}", event.outboxId(), e);
        }
        return String.valueOf(event.outboxId());
    }
}
