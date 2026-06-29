package com.baseball.waiting_room_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class CapacitySignalKafkaPublisherTest {

    private SimpleMeterRegistry meterRegistry;
    private CapacitySignalKafkaPublisher publisher;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        publisher = new CapacitySignalKafkaPublisher(
                (KafkaTemplate<String, String>) null,
                new ObjectMapper(),
                meterRegistry,
                true,
                "capacity.signals");
    }

    @Test
    void publishesOnlyWhenDbPressureLevelChanges() {
        recordDecision("NORMAL");
        assertThat(failureCounter().count()).isZero();

        recordDecision("CAUTION");
        assertThat(failureCounter().count()).isEqualTo(1.0);

        recordDecision("CAUTION");
        assertThat(failureCounter().count()).isEqualTo(1.0);

        recordDecision("NORMAL");
        assertThat(failureCounter().count()).isEqualTo(2.0);
    }

    private Counter failureCounter() {
        return meterRegistry
                .find("capacity_signal_kafka_publish_total")
                .tag("topic", "capacity.signals")
                .tag("result", "failure")
                .counter();
    }

    private void recordDecision(String dbPressureLevel) {
        int throttlePercent = "NORMAL".equals(dbPressureLevel) ? 100 : 75;
        int effectiveEnterPerMinute = "NORMAL".equals(dbPressureLevel) ? 40 : 30;
        publisher.recordAdmissionDecision(
                9001L,
                dbPressureLevel,
                "NORMAL".equals(dbPressureLevel) ? 18 : 43,
                60,
                throttlePercent,
                40,
                2,
                2,
                40,
                effectiveEnterPerMinute,
                effectiveEnterPerMinute,
                30,
                true);
    }
}
