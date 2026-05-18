package com.baseball.seat_lock_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatLockResponse {
    private String lockId;
    private Long gameId;
    private Long seatId;
    private Long userId;
    private String status;
    private Long expiresIn;
}
