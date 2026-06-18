package com.baseball.waiting_room_service.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DbPressurePolicyTest {

    @Test
    void evaluatesConnectionPressureBoundaries() {
        assertPressure(39, 100, "NORMAL");
        assertPressure(40, 75, "CAUTION");
        assertPressure(49, 75, "CAUTION");
        assertPressure(50, 50, "WARNING");
        assertPressure(54, 50, "WARNING");
        assertPressure(55, 25, "CRITICAL");
        assertPressure(59, 25, "CRITICAL");
        assertPressure(60, 0, "STOP");
        assertPressure(79, 0, "STOP");
    }

    private void assertPressure(int connections, int expectedPercent, String expectedLevel) {
        DbPressurePolicy.Result result = DbPressurePolicy.evaluate(connections, 60, 40, 50, 55);

        assertThat(result.throttlePercent()).isEqualTo(expectedPercent);
        assertThat(result.level()).isEqualTo(expectedLevel);
        assertThat(result.budget()).isEqualTo(60);
    }
}
