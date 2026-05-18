package com.baseball.seat_lock_service.controller;

import com.baseball.seat_lock_service.dto.SeatLockRequest;
import com.baseball.seat_lock_service.dto.SeatLockResponse;
import com.baseball.seat_lock_service.service.SeatLockService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/seats/locks")
@RequiredArgsConstructor
public class SeatLockController {

    private final SeatLockService seatLockService;

    @PostMapping
    public ResponseEntity<SeatLockResponse> lockSeat(
            @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId,
            @RequestBody SeatLockRequest request) {

        String lockId = seatLockService.lockSeat(request.getGameId(), request.getSeatId(), userId);

        SeatLockResponse response = SeatLockResponse.builder()
                .lockId(lockId)
                .gameId(request.getGameId())
                .seatId(request.getSeatId())
                .userId(userId)
                .status("LOCKED")
                .expiresIn(300L) // 5분 (초 단위)
                .build();

        return ResponseEntity.ok(response);
    }

    @DeleteMapping
    public ResponseEntity<Void> unlockSeat(
            @RequestParam Long gameId, @RequestParam Long seatId, @RequestParam String lockId) {
        seatLockService.unlockSeat(gameId, seatId, lockId);
        return ResponseEntity.ok().build();
    }
}
