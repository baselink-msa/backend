package com.baseball.admin.controller;

import com.baseball.admin.common.ApiResponse;
import com.baseball.admin.dto.*;
import com.baseball.admin.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @PostMapping("/games")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createGame(
            @Valid @RequestBody CreateGameRequest request) {
        return created(adminService.createGame(request), "경기가 등록되었습니다.");
    }

    @PostMapping("/seat-sections")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createSeatSection(
            @Valid @RequestBody CreateSeatSectionRequest request) {
        return created(adminService.createSeatSection(request), "좌석 구역이 등록되었습니다.");
    }

    @PostMapping("/seats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createSeat(
            @Valid @RequestBody CreateSeatRequest request) {
        return created(adminService.createSeat(request), "좌석이 등록되었습니다.");
    }

    @PostMapping("/games/{gameId}/seats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createGameSeats(
            @PathVariable Long gameId,
            @Valid @RequestBody CreateGameSeatsRequest request) {
        return created(adminService.createGameSeats(gameId, request), "경기 좌석이 생성되었습니다.");
    }

    @PutMapping("/games/{gameId}/waiting-room-policy")
    public ApiResponse<Map<String, Object>> upsertWaitingRoomPolicy(
            @PathVariable Long gameId,
            @Valid @RequestBody UpsertWaitingRoomPolicyRequest request) {
        return ApiResponse.ok(adminService.upsertWaitingRoomPolicy(gameId, request),
                "대기열 정책이 저장되었습니다.");
    }

    @PostMapping("/menus")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createMenu(
            @Valid @RequestBody CreateMenuRequest request) {
        return created(adminService.createMenu(request), "메뉴가 등록되었습니다.");
    }

    @PostMapping("/faqs")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createFaq(
            @Valid @RequestBody CreateFaqRequest request) {
        return created(adminService.createFaq(request), "FAQ가 등록되었습니다.");
    }

    @GetMapping("/waiting-room/games/{gameId}")
    public ApiResponse<Map<String, Object>> getWaitingRoomStatus(@PathVariable Long gameId) {
        return ApiResponse.ok(adminService.getWaitingRoomStatus(gameId));
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> created(
            Map<String, Object> data, String message) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(data, message));
    }
}
