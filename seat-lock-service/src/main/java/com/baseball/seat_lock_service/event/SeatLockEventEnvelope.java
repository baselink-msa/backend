package com.baseball.seat_lock_service.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record SeatLockEventEnvelope(
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

    public static SeatLockEventEnvelope create(
            String eventType,
            Long gameId,
            Long seatId,
            Map<String, Object> payload) {
        return new SeatLockEventEnvelope(
                UUID.randomUUID(),
                eventType,
                1,
                Instant.now(),
                "seat-lock-service",
                "SEAT_LOCK",
                "game-" + gameId + ":seat-" + seatId,
                gameId,
                null,
                null,
                Map.copyOf(payload));
    }
}
