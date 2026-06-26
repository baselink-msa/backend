package com.baseball.waiting_room_service.service;

import com.baseball.waiting_room_service.event.WaitingEventEnvelope;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
public class WaitingEventPublisher {

    private final SqsTemplate sqsTemplate;
    private final WaitingOperationalKafkaPublisher kafkaPublisher;
    private final String queueName;
    private final Counter publishedCounter;
    private final Counter failedCounter;

    public WaitingEventPublisher(
            SqsTemplate sqsTemplate,
            WaitingOperationalKafkaPublisher kafkaPublisher,
            MeterRegistry meterRegistry,
            @Value("${app.ticket-events.queue-name:ticket-domain-events}") String queueName) {
        this.sqsTemplate = sqsTemplate;
        this.kafkaPublisher = kafkaPublisher;
        this.queueName = queueName;
        this.publishedCounter = Counter.builder("waiting_event_publish_total")
                .tag("result", "success")
                .register(meterRegistry);
        this.failedCounter = Counter.builder("waiting_event_publish_total")
                .tag("result", "failure")
                .register(meterRegistry);
    }

    @Async("waitingEventExecutor")
    public void publishWaitingEntered(
            Long gameId,
            long initialRank,
            int policyMaxEnterPerMinute) {
        publish(WaitingEventEnvelope.create(
                "WAITING_ENTERED",
                gameId,
                Map.of(
                        "initialRank", initialRank,
                        "policyMaxEnterPerMinute", policyMaxEnterPerMinute)));
    }

    @Async("waitingEventExecutor")
    public void publishAccessTokenIssued(
            Long gameId,
            long waitingSeconds,
            int effectiveEnterPerMinute,
            String dbPressureLevel,
            int dbThrottlePercent) {
        publish(WaitingEventEnvelope.create(
                "ACCESS_TOKEN_ISSUED",
                gameId,
                Map.of(
                        "waitingSeconds", waitingSeconds,
                        "effectiveEnterPerMinute", effectiveEnterPerMinute,
                        "dbPressureLevel", dbPressureLevel,
                        "dbThrottlePercent", dbThrottlePercent)));
    }

    private void publish(WaitingEventEnvelope event) {
        try {
            sqsTemplate.send(to -> to.queue(queueName).payload(event));
            publishToKafka(event);
            publishedCounter.increment();
            log.debug("대기열 이벤트 발행 완료: eventType={}, eventId={}",
                    event.eventType(), event.eventId());
        } catch (Exception exception) {
            failedCounter.increment();
            log.warn("대기열 이벤트 발행 실패: eventType={}, eventId={}",
                    event.eventType(), event.eventId(), exception);
        }
    }

    private void publishToKafka(WaitingEventEnvelope event) {
        try {
            kafkaPublisher.publish(event);
        } catch (Exception e) {
            log.warn("Kafka 보조 발행 실패를 대기열 이벤트 실패로 처리하지 않습니다: eventType={}, eventId={}",
                    event.eventType(), event.eventId(), e);
        }
    }
}
