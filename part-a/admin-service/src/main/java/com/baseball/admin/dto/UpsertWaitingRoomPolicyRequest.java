package com.baseball.admin.dto;

import jakarta.validation.constraints.Positive;

public record UpsertWaitingRoomPolicyRequest(
        @Positive Integer maxEnterPerMinute,
        @Positive Integer tokenTtlSeconds,
        Boolean enabled
) {
}
