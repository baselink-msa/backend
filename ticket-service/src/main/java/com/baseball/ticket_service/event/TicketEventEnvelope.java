package com.baseball.ticket_service.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record TicketEventEnvelope(
        UUID eventId,
        TicketEventType eventType,
        int schemaVersion,
        Instant occurredAt,
        String producer,
        String aggregateType,
        String aggregateId,
        Long gameId,
        String userKey,
        String traceId,
        Map<String, Object> payload) {

    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final String PRODUCER = "ticket-service";
    public static final String RESERVATION_AGGREGATE = "RESERVATION";

    public static TicketEventEnvelope reservation(
            TicketEventType eventType,
            Long reservationId,
            Long gameId,
            String userKey,
            Map<String, Object> payload) {
        return new TicketEventEnvelope(
                UUID.randomUUID(),
                eventType,
                CURRENT_SCHEMA_VERSION,
                Instant.now(),
                PRODUCER,
                RESERVATION_AGGREGATE,
                reservationId.toString(),
                gameId,
                userKey,
                null,
                Map.copyOf(payload));
    }
}
