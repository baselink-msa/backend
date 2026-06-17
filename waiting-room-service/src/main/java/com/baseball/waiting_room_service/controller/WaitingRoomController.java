package com.baseball.waiting_room_service.controller;

import com.baseball.waiting_room_service.dto.ApiResponse;
import com.baseball.waiting_room_service.dto.TokenResponse;
import com.baseball.waiting_room_service.dto.WaitingResponse;
import com.baseball.waiting_room_service.dto.WaitingRoomPolicy;
import com.baseball.waiting_room_service.service.WaitingRoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/waiting-room/games")
@RequiredArgsConstructor
public class WaitingRoomController {

    private final WaitingRoomService waitingRoomService;

    /**
     * 1. 대기열 입장 API
     */
    @PostMapping("/{gameId}/enter")
    public ResponseEntity<ApiResponse<WaitingResponse>> enterWaitingRoom(
            @PathVariable("gameId") Long gameId,
            @RequestHeader(value = "X-User-Id") Long userId) {
        
        Long rank = waitingRoomService.enterWaitingRoom(gameId, userId);
        WaitingRoomPolicy policy = waitingRoomService.getWaitingRoomPolicy(gameId);
        WaitingResponse response = buildWaitingResponse(gameId, userId, rank, policy);

        return ResponseEntity.ok(new ApiResponse<>(true, response, "대기열에 등록되었습니다."));
    }

    /**
     * 2. 내 대기 상태 조회 API (프론트엔드에서 폴링으로 호출)
     */
    @GetMapping("/{gameId}/status")
    public ResponseEntity<ApiResponse<WaitingResponse>> getWaitingStatus(
            @PathVariable("gameId") Long gameId,
            @RequestHeader(value = "X-User-Id") Long userId) {

        Long rank = waitingRoomService.getWaitingPosition(gameId, userId);
        
        if (rank == -1L) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(false, null, "대기열 정보를 찾을 수 없습니다."));
        }

        WaitingRoomPolicy policy = waitingRoomService.getWaitingRoomPolicy(gameId);
        WaitingResponse response = buildWaitingResponse(gameId, userId, rank, policy);
        return ResponseEntity.ok(new ApiResponse<>(true, response, "대기 상태 조회 성공"));
    }

    /**
     * 3. 입장 허용 토큰 발급 API
     */
    @PostMapping("/{gameId}/issue-token")
    public ResponseEntity<ApiResponse<?>> issueToken(
            @PathVariable("gameId") Long gameId,
            @RequestHeader(value = "X-User-Id") Long userId) {

        Long rank = waitingRoomService.getWaitingPosition(gameId, userId);
        WaitingRoomPolicy policy = waitingRoomService.getWaitingRoomPolicy(gameId);

        if (!waitingRoomService.isRankAllowed(rank, policy)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ApiResponse<>(false, null, "아직 입장 순서가 아닙니다."));
        }

        if (!waitingRoomService.consumeEntrySlot(gameId, policy)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new ApiResponse<>(false, null, "이번 분 입장 가능 인원이 모두 소진되었습니다."));
        }

        if (rank > 0) {
            String token = waitingRoomService.issueAccessToken(gameId, userId, policy.tokenTtlSeconds());
            TokenResponse tokenResponse = TokenResponse.builder()
                    .ticketAccessToken(token)
                    .expiresIn((long) policy.tokenTtlSeconds())
                    .build();
            return ResponseEntity.ok(new ApiResponse<>(true, tokenResponse, "좌석 선택 화면에 입장할 수 있습니다."));
        }

        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiResponse<>(false, null, "아직 입장 순서가 아닙니다."));
    }

    // 응답 객체 생성 공통 메서드
    private WaitingResponse buildWaitingResponse(Long gameId, Long userId, Long rank, WaitingRoomPolicy policy) {
        long peopleAhead = Math.max(0, rank - 1);
        boolean rankAllowed = waitingRoomService.isRankAllowed(rank, policy);
        boolean capacityAvailable = waitingRoomService.hasEntryCapacity(gameId, policy);
        boolean canEnter = rankAllowed && capacityAvailable;
        long estimatedWaitSeconds = estimateWaitSeconds(rank, canEnter, rankAllowed, capacityAvailable, policy);

        return WaitingResponse.builder()
                .gameId(gameId)
                .userId(userId)
                .status(canEnter ? "ALLOWED" : "WAITING")
                .position(rank)
                .peopleAhead(peopleAhead)
                .estimatedWaitSeconds(estimatedWaitSeconds)
                .canEnter(canEnter)
                .build();
    }

    private long estimateWaitSeconds(Long rank, boolean canEnter, boolean rankAllowed,
                                     boolean capacityAvailable, WaitingRoomPolicy policy) {
        if (canEnter || !policy.enabled()) {
            return 0L;
        }

        if (rankAllowed && !capacityAvailable) {
            return 60L;
        }

        int effectiveEnterPerMinute = waitingRoomService.getEffectiveEnterPerMinute(policy);
        if (effectiveEnterPerMinute <= 0) {
            return 60L;
        }

        long overflowRank = Math.max(0, rank - effectiveEnterPerMinute);
        return Math.max(60L, (long) Math.ceil((double) overflowRank / effectiveEnterPerMinute) * 60);
    }
}
