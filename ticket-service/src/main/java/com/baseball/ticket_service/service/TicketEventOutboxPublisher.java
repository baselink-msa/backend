package com.baseball.ticket_service.service;

import com.baseball.ticket_service.entity.OutboxDestination;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class TicketEventOutboxPublisher {

    private final TicketEventOutboxClaimService claimService;
    private final SqsTemplate sqsTemplate;
    private final String domainEventQueueName;
    private final String ticketConfirmQueueName;
    private final int batchSize;
    private final int maxAttempts;
    private final Duration leaseDuration;
    private final String instanceId;
    private final Counter publishedCounter;
    private final Counter failedCounter;
    private final Counter retryCounter;

    public TicketEventOutboxPublisher(
            TicketEventOutboxClaimService claimService,
            SqsTemplate sqsTemplate,
            MeterRegistry meterRegistry,
            @Value("${app.ticket-events.queue-name:ticket-domain-events}") String domainEventQueueName,
            @Value("${app.ticket-events.ticket-confirm-queue-name:ticket-confirm-queue}") String ticketConfirmQueueName,
            @Value("${app.ticket-events.publisher.batch-size:20}") int batchSize,
            @Value("${app.ticket-events.publisher.max-attempts:10}") int maxAttempts,
            @Value("${app.ticket-events.publisher.lease-seconds:600}") long leaseSeconds) {
        this.claimService = claimService;
        this.sqsTemplate = sqsTemplate;
        this.domainEventQueueName = domainEventQueueName;
        this.ticketConfirmQueueName = ticketConfirmQueueName;
        this.batchSize = Math.max(1, batchSize);
        this.maxAttempts = Math.max(1, maxAttempts);
        this.leaseDuration = Duration.ofSeconds(Math.max(30, leaseSeconds));
        this.instanceId = resolveInstanceId();
        this.publishedCounter = Counter.builder("ticket_outbox_publish_total")
                .tag("result", "success")
                .register(meterRegistry);
        this.failedCounter = Counter.builder("ticket_outbox_publish_total")
                .tag("result", "failure")
                .register(meterRegistry);
        this.retryCounter = Counter.builder("ticket_outbox_retry_total")
                .register(meterRegistry);
        Gauge.builder(
                        "ticket_outbox_pending",
                        claimService,
                        service -> service.countPending(this.maxAttempts))
                .register(meterRegistry);
        Gauge.builder(
                        "ticket_outbox_oldest_pending_seconds",
                        claimService,
                        service -> service.oldestPendingSeconds(this.maxAttempts))
                .register(meterRegistry);
        Gauge.builder(
                        "ticket_outbox_failed",
                        claimService,
                        service -> service.countTerminalFailed(this.maxAttempts))
                .register(meterRegistry);
    }

    @Scheduled(
            fixedDelayString = "${app.ticket-events.publisher.fixed-delay-ms:2000}",
            initialDelayString = "${app.ticket-events.publisher.initial-delay-ms:10000}")
    public void publishPendingEvents() {
        List<ClaimedOutboxEvent> events = claimService.claim(instanceId, batchSize, maxAttempts);
        for (ClaimedOutboxEvent event : events) {
            publish(event);
        }
    }

    @Scheduled(
            fixedDelayString = "${app.ticket-events.publisher.lease-recovery-delay-ms:60000}",
            initialDelayString = "${app.ticket-events.publisher.initial-delay-ms:10000}")
    public void recoverExpiredLeases() {
        int recovered = claimService.recoverExpiredLeases(Instant.now().minus(leaseDuration));
        if (recovered > 0) {
            log.warn("만료된 Outbox publisher lease를 복구했습니다: count={}", recovered);
        }
    }

    private void publish(ClaimedOutboxEvent event) {
        try {
            sqsTemplate.send(to -> to.queue(resolveQueueName(event.destination())).payload(event.payload()));
            claimService.markPublished(event.outboxId());
            publishedCounter.increment();
            log.debug("Outbox event 발행 완료: outboxId={}, attempts={}", event.outboxId(), event.attempts());
        } catch (Exception e) {
            Duration backoff = retryBackoff(event.attempts());
            claimService.markFailed(
                    event.outboxId(),
                    Instant.now().plus(backoff),
                    rootMessage(e));
            failedCounter.increment();
            retryCounter.increment();
            log.warn(
                    "Outbox event 발행 실패: outboxId={}, attempts={}, retryAfterSeconds={}",
                    event.outboxId(),
                    event.attempts(),
                    backoff.toSeconds(),
                    e);
        }
    }

    private String resolveQueueName(OutboxDestination destination) {
        return switch (destination) {
            case TICKET_CONFIRM -> ticketConfirmQueueName;
            case DOMAIN_EVENTS -> domainEventQueueName;
        };
    }

    private Duration retryBackoff(int attempts) {
        long seconds = Math.min(900, 60L * (1L << Math.min(Math.max(0, attempts - 1), 4)));
        return Duration.ofSeconds(seconds);
    }

    private String resolveInstanceId() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {
            return "ticket-service-" + UUID.randomUUID();
        }
    }

    private String rootMessage(Exception exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
