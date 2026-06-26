package com.baseball.ticket_service.service;

import com.baseball.ticket_service.entity.OutboxDestination;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketEventOutboxPublisherTest {

    @Mock
    private TicketEventOutboxClaimService claimService;

    @Mock
    private SqsTemplate sqsTemplate;

    @Mock
    private TicketDomainKafkaPublisher kafkaPublisher;

    private TicketEventOutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new TicketEventOutboxPublisher(
                claimService,
                sqsTemplate,
                kafkaPublisher,
                new SimpleMeterRegistry(),
                "ticket-domain-events",
                "ticket-confirm-queue",
                20,
                10,
                600);
    }

    @Test
    void marksClaimedEventPublishedAfterSuccessfulSend() {
        ClaimedOutboxEvent event = new ClaimedOutboxEvent(
                11L,
                OutboxDestination.DOMAIN_EVENTS,
                "{\"eventId\":\"event-1\"}",
                1);
        when(claimService.claim(any(String.class), eq(20), eq(10)))
                .thenReturn(List.of(event));

        publisher.publishPendingEvents();

        verify(kafkaPublisher).publishDomainEvent(event);
        verify(claimService).markPublished(11L);
        verify(claimService, never()).markFailed(eq(11L), any(Instant.class), any(String.class));
    }

    @Test
    void doesNotPublishTicketConfirmCommandToKafka() {
        ClaimedOutboxEvent event = new ClaimedOutboxEvent(
                13L,
                OutboxDestination.TICKET_CONFIRM,
                "{\"reservationId\":381}",
                1);
        when(claimService.claim(any(String.class), eq(20), eq(10)))
                .thenReturn(List.of(event));

        publisher.publishPendingEvents();

        verify(kafkaPublisher, never()).publishDomainEvent(any(ClaimedOutboxEvent.class));
        verify(claimService).markPublished(13L);
    }

    @Test
    void marksEventPublishedWhenKafkaBestEffortPublishFailsAfterSqsSend() {
        ClaimedOutboxEvent event = new ClaimedOutboxEvent(
                14L,
                OutboxDestination.DOMAIN_EVENTS,
                "{\"eventId\":\"event-3\"}",
                1);
        when(claimService.claim(any(String.class), eq(20), eq(10)))
                .thenReturn(List.of(event));
        doThrow(new IllegalStateException("Kafka unavailable"))
                .when(kafkaPublisher)
                .publishDomainEvent(event);

        publisher.publishPendingEvents();

        verify(claimService).markPublished(14L);
        verify(claimService, never()).markFailed(eq(14L), any(Instant.class), any(String.class));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void marksEventFailedWithBackoffWhenSendFails() {
        ClaimedOutboxEvent event = new ClaimedOutboxEvent(
                12L,
                OutboxDestination.TICKET_CONFIRM,
                "{\"eventId\":\"event-2\"}",
                2);
        when(claimService.claim(any(String.class), eq(20), eq(10)))
                .thenReturn(List.of(event));
        doThrow(new IllegalStateException("SQS unavailable"))
                .when(sqsTemplate)
                .send(any(Consumer.class));

        Instant before = Instant.now();
        publisher.publishPendingEvents();

        ArgumentCaptor<Instant> retryAt = ArgumentCaptor.forClass(Instant.class);
        verify(claimService).markFailed(eq(12L), retryAt.capture(), eq("SQS unavailable"));
        assertThat(retryAt.getValue()).isAfterOrEqualTo(before.plusSeconds(119));
        assertThat(retryAt.getValue()).isBeforeOrEqualTo(before.plusSeconds(125));
        verify(claimService, never()).markPublished(12L);
    }

    @Test
    void recoversExpiredLeaseBeforeClaimingNextBatch() {
        when(claimService.recoverExpiredLeases(any(Instant.class))).thenReturn(2);

        publisher.recoverExpiredLeases();

        verify(claimService).recoverExpiredLeases(any(Instant.class));
    }
}
