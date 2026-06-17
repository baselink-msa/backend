package com.baseball.waiting_room_service.dto;

public record WaitingRoomPolicy(
        int maxEnterPerMinute,
        int tokenTtlSeconds,
        boolean enabled
) {
}
