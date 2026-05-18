package com.baseball.seat_lock_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeatLockRequest {
    private Long gameId;
    private Long seatId;
}
