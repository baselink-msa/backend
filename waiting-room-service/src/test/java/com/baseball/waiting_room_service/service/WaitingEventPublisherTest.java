package com.baseball.waiting_room_service.service;

import com.baseball.waiting_room_service.event.WaitingEventEnvelope;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class WaitingEventPublisherTest {

    private SqsTemplate sqsTemplate;
    private WaitingEventPublisher publisher;

    @BeforeEach
    void setUp() {
        sqsTemplate = mock(SqsTemplate.class);
        publisher = new WaitingEventPublisher(
                sqsTemplate,
                new SimpleMeterRegistry(),
                "ticket-domain-events");
    }

    @Test
    void publishesWaitingEnteredAndAccessTokenIssued() {
        publisher.publishWaitingEntered(1L, 42L, 100);
        publisher.publishAccessTokenIssued(1L, 87L, 30, "CAUTION", 75);

        verify(sqsTemplate, times(2)).send(any(java.util.function.Consumer.class));
    }

    @Test
    void publishingFailureDoesNotEscapeToAdmissionFlow() {
        doThrow(new IllegalStateException("SQS unavailable"))
                .when(sqsTemplate)
                .send(any(java.util.function.Consumer.class));

        assertDoesNotThrow(() ->
                publisher.publishWaitingEntered(1L, 42L, 100));
    }

    @Test
    void envelopeDoesNotStoreRawUserIdentifier() {
        WaitingEventEnvelope event = WaitingEventEnvelope.create(
                "WAITING_ENTERED",
                1L,
                java.util.Map.of("initialRank", 42L));

        assertEquals(event.eventId().toString(), event.aggregateId());
        assertNull(event.userKey());
    }
}
