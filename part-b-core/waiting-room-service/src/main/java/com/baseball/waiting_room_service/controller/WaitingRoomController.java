package com.baseball.waiting_room_service.controller;

import com.baseball.waiting_room_service.dto.ApiResponse;
import com.baseball.waiting_room_service.dto.TokenResponse;
import com.baseball.waiting_room_service.dto.WaitingResponse;
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

    // 분당 입장 허용 인원 (임시 설정)
    private static final int ENTER_PER_MINUTE = 100; 

    /**
     * 1. 대기열 입장 API
     */
    @PostMapping("/{gameId}/enter")
    public ResponseEntity<ApiResponse<WaitingResponse>> enterWaitingRoom(
            @PathVariable("gameId") Long gameId,
            @RequestHeader(value = "X-User-Id") Long userId) {
        
        Long rank = waitingRoomService.enterWaitingRoom(gameId, userId);
        WaitingResponse response = buildWaitingResponse(gameId, userId, rank);

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

        WaitingResponse response = buildWaitingResponse(gameId, userId, rank);
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
        
        // 내 순번이 100번 이내(1~100)일 때만 토큰 발급 허용
        if (rank > 0 && rank <= ENTER_PER_MINUTE) {
            String token = waitingRoomService.issueAccessToken(gameId, userId);
            TokenResponse tokenResponse = TokenResponse.builder()
                    .ticketAccessToken(token)
                    .expiresIn(300L) // 5분
                    .build();
            return ResponseEntity.ok(new ApiResponse<>(true, tokenResponse, "좌석 선택 화면에 입장할 수 있습니다."));
        }

        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiResponse<>(false, null, "아직 입장 순서가 아닙니다."));
    }

    // 응답 객체 생성 공통 메서드
    private WaitingResponse buildWaitingResponse(Long gameId, Long userId, Long rank) {
        long peopleAhead = Math.max(0, rank - 1);
        // 100명당 60초 소요 가정
        long estimatedWaitSeconds = (long) Math.ceil((double) peopleAhead / ENTER_PER_MINUTE) * 60;
        boolean canEnter = rank > 0 && rank <= ENTER_PER_MINUTE;

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
}