package com.baseball.waiting_room_service.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WaitingResponse {
    private Long gameId;
    private Long userId;
    private String status;
    private Long position;
    private Long peopleAhead;
    private Long estimatedWaitSeconds;
    private Long serverTimeEpochMillis;
    private Integer nextCheckAfterSeconds;
    private Integer policyMaxEnterPerMinute;
    private Integer currentReadyPodCount;
    private Integer projectedReadyPodCount;
    private Integer effectiveEnterPerMinute;
    private Integer projectedEnterPerMinute;
    private Long currentMinuteRemainingSlots;
    private boolean canEnter;
}
