package com.baseball.seat_lock_service.event;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SeatLockEventEnvelopeTest {

    @Test
    void createsSeatLockEnvelopeWithoutSensitiveLockId() {
        SeatLockEventEnvelope event = SeatLockEventEnvelope.create(
                "SEAT_LOCKED",
                9001L,
                123L,
                Map.of(
                        "gameId", 9001L,
                        "seatId", 123L,
                        "status", "LOCKED",
                        "lockTtlSeconds", 300L));

        assertThat(event.eventType()).isEqualTo("SEAT_LOCKED");
        assertThat(event.schemaVersion()).isEqualTo(1);
        assertThat(event.producer()).isEqualTo("seat-lock-service");
        assertThat(event.aggregateType()).isEqualTo("SEAT_LOCK");
        assertThat(event.aggregateId()).isEqualTo("game-9001:seat-123");
        assertThat(event.gameId()).isEqualTo(9001L);
        assertThat(event.userKey()).isNull();
        assertThat(event.payload()).containsEntry("seatId", 123L);
        assertThat(event.payload()).doesNotContainKey("lockId");
    }
}
