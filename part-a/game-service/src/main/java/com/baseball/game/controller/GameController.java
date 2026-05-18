package com.baseball.game.controller;

import com.baseball.game.common.ApiResponse;
import com.baseball.game.dto.GameDetailResponse;
import com.baseball.game.dto.GameListItem;
import com.baseball.game.dto.GameSeatResponse;
import com.baseball.game.dto.SeatSectionResponse;
import com.baseball.game.service.GameService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/games")
@RequiredArgsConstructor
public class GameController {

    private final GameService gameService;

    @GetMapping
    public ApiResponse<List<GameListItem>> getGames(
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(gameService.getGames(status));
    }

    @GetMapping("/{gameId}")
    public ApiResponse<GameDetailResponse> getGame(@PathVariable Long gameId) {
        return ApiResponse.ok(gameService.getGame(gameId));
    }

    @GetMapping("/{gameId}/seat-sections")
    public ApiResponse<List<SeatSectionResponse>> getSeatSections(@PathVariable Long gameId) {
        return ApiResponse.ok(gameService.getSeatSections(gameId));
    }

    @GetMapping("/{gameId}/seats")
    public ApiResponse<List<GameSeatResponse>> getSeats(
            @PathVariable Long gameId,
            @RequestParam(required = false) Long sectionId,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(gameService.getSeats(gameId, sectionId, status));
    }
}
