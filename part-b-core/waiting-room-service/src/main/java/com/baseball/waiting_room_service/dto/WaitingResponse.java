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
    private boolean canEnter;
}