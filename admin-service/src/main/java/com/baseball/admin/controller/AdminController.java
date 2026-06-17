package com.baseball.admin.controller;

import com.baseball.admin.common.ApiResponse;
import com.baseball.admin.dto.*;
import com.baseball.admin.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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

    @GetMapping("/games/{gameId}/waiting-room-policy")
    public ApiResponse<Map<String, Object>> getWaitingRoomPolicy(@PathVariable Long gameId) {
        return ApiResponse.ok(adminService.getWaitingRoomPolicy(gameId),
                "대기열 정책 조회 성공");
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

    // ==================== 경기 수정/상태 변경/삭제 ====================

    @PutMapping("/games/{gameId}")
    public ApiResponse<Map<String, Object>> updateGame(
            @PathVariable Long gameId,
            @Valid @RequestBody CreateGameRequest request) {
        return ApiResponse.ok(adminService.updateGame(gameId, request), "경기가 수정되었습니다.");
    }

    @PatchMapping("/games/{gameId}/status")
    public ApiResponse<Map<String, Object>> changeGameStatus(
            @PathVariable Long gameId,
            @RequestBody Map<String, String> body) {
        return ApiResponse.ok(adminService.changeGameStatus(gameId, body.get("status")), "경기 상태가 변경되었습니다.");
    }

    @DeleteMapping("/games/{gameId}")
    public ResponseEntity<Void> deleteGame(@PathVariable Long gameId) {
        adminService.deleteGame(gameId);
        return ResponseEntity.noContent().build();
    }

    // ==================== 좌석 구역 수정/삭제 ====================

    @PutMapping("/seat-sections/{sectionId}")
    public ApiResponse<Map<String, Object>> updateSeatSection(
            @PathVariable Long sectionId,
            @Valid @RequestBody CreateSeatSectionRequest request) {
        return ApiResponse.ok(adminService.updateSeatSection(sectionId, request), "좌석 구역이 수정되었습니다.");
    }

    @DeleteMapping("/seat-sections/{sectionId}")
    public ResponseEntity<Void> deleteSeatSection(@PathVariable Long sectionId) {
        adminService.deleteSeatSection(sectionId);
        return ResponseEntity.noContent().build();
    }

    // ==================== 좌석 조회/수정/삭제 ====================

    @GetMapping("/seats")
    public ApiResponse<List<Map<String, Object>>> getSeats(@RequestParam Long stadiumId) {
        return ApiResponse.ok(adminService.getSeats(stadiumId));
    }

    @PutMapping("/seats/{seatId}")
    public ApiResponse<Map<String, Object>> updateSeat(
            @PathVariable Long seatId,
            @Valid @RequestBody CreateSeatRequest request) {
        return ApiResponse.ok(adminService.updateSeat(seatId, request), "좌석이 수정되었습니다.");
    }

    @DeleteMapping("/seats/{seatId}")
    public ResponseEntity<Void> deleteSeat(@PathVariable Long seatId) {
        adminService.deleteSeat(seatId);
        return ResponseEntity.noContent().build();
    }

    // ==================== 경기 좌석 조회/가격 변경/삭제 ====================

    @GetMapping("/games/{gameId}/seats")
    public ApiResponse<List<Map<String, Object>>> getGameSeats(@PathVariable Long gameId) {
        return ApiResponse.ok(adminService.getGameSeats(gameId));
    }

    @PatchMapping("/game-seats/{gameSeatId}/price")
    public ApiResponse<Map<String, Object>> updateGameSeatPrice(
            @PathVariable Long gameSeatId,
            @RequestBody Map<String, Integer> body) {
        return ApiResponse.ok(adminService.updateGameSeatPrice(gameSeatId, body.get("price")), "가격이 변경되었습니다.");
    }

    @DeleteMapping("/game-seats/{gameSeatId}")
    public ResponseEntity<Void> deleteGameSeat(@PathVariable Long gameSeatId) {
        adminService.deleteGameSeat(gameSeatId);
        return ResponseEntity.noContent().build();
    }

    // ==================== 메뉴 수정/삭제 ====================

    @PutMapping("/menus/{menuId}")
    public ApiResponse<Map<String, Object>> updateMenu(
            @PathVariable Long menuId,
            @Valid @RequestBody CreateMenuRequest request) {
        return ApiResponse.ok(adminService.updateMenu(menuId, request), "메뉴가 수정되었습니다.");
    }

    @DeleteMapping("/menus/{menuId}")
    public ResponseEntity<Void> deleteMenu(@PathVariable Long menuId) {
        adminService.deleteMenu(menuId);
        return ResponseEntity.noContent().build();
    }

    // ==================== FAQ 수정/삭제 ====================

    @PutMapping("/faqs/{faqId}")
    public ApiResponse<Map<String, Object>> updateFaq(
            @PathVariable Long faqId,
            @Valid @RequestBody CreateFaqRequest request) {
        return ApiResponse.ok(adminService.updateFaq(faqId, request), "FAQ가 수정되었습니다.");
    }

    @DeleteMapping("/faqs/{faqId}")
    public ResponseEntity<Void> deleteFaq(@PathVariable Long faqId) {
        adminService.deleteFaq(faqId);
        return ResponseEntity.noContent().build();
    }

    // ==================== 구장 조회/등록/수정/삭제 ====================

    @GetMapping("/stadiums")
    public ApiResponse<List<Map<String, Object>>> getStadiums() {
        return ApiResponse.ok(adminService.getStadiums());
    }

    @PostMapping("/stadiums")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createStadium(
            @Valid @RequestBody CreateStadiumRequest request) {
        return created(adminService.createStadium(request), "구장이 등록되었습니다.");
    }

    @PutMapping("/stadiums/{stadiumId}")
    public ApiResponse<Map<String, Object>> updateStadium(
            @PathVariable Long stadiumId,
            @Valid @RequestBody CreateStadiumRequest request) {
        return ApiResponse.ok(adminService.updateStadium(stadiumId, request), "구장이 수정되었습니다.");
    }

    @DeleteMapping("/stadiums/{stadiumId}")
    public ResponseEntity<Void> deleteStadium(@PathVariable Long stadiumId) {
        adminService.deleteStadium(stadiumId);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> created(
            Map<String, Object> data, String message) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(data, message));
    }
}
