package com.baseball.waiting_room_service.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record CapacitySignalEventEnvelope(
        UUID eventId,
        String eventType,
        int schemaVersion,
        Instant occurredAt,
        String producer,
        String aggregateType,
        String aggregateId,
        Long gameId,
        String userKey,
        String traceId,
        Map<String, Object> payload) {

    public static CapacitySignalEventEnvelope create(
            String eventType,
            Long gameId,
            Map<String, Object> payload) {
        UUID eventId = UUID.randomUUID();
        return new CapacitySignalEventEnvelope(
                eventId,
                eventType,
                1,
                Instant.now(),
                "waiting-room-service",
                "CAPACITY_SIGNAL",
                gameId == null ? eventId.toString() : "game-" + gameId,
                gameId,
                null,
                null,
                Map.copyOf(payload));
    }
}
