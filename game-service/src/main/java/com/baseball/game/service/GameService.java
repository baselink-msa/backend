package com.baseball.game.service;

import com.baseball.game.common.BusinessException;
import com.baseball.game.domain.Game;
import com.baseball.game.domain.GameSeatStatus;
import com.baseball.game.domain.GameStatus;
import com.baseball.game.dto.GameDetailResponse;
import com.baseball.game.dto.GameListItem;
import com.baseball.game.dto.GameSeatResponse;
import com.baseball.game.dto.SeatSectionResponse;
import com.baseball.game.repository.GameRepository;
import com.baseball.game.repository.GameSeatRepository;
import com.baseball.game.repository.SeatSectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GameService {

    private final GameRepository gameRepository;
    private final SeatSectionRepository seatSectionRepository;
    private final GameSeatRepository gameSeatRepository;

    @Transactional(readOnly = true)
    public List<GameListItem> getGames(String status) {
        List<Game> games;
        if (status != null && !status.isBlank()) {
            games = gameRepository.findByStatusOrderByGameStartTimeDesc(parseStatus(status));
        } else {
            games = gameRepository.findAllByOrderByGameStartTimeDesc();
        }
        return games.stream().map(GameListItem::from).toList();
    }

    @Transactional(readOnly = true)
    public GameDetailResponse getGame(Long gameId) {
        Game game = gameRepository.findByGameId(gameId)
                .orElseThrow(() -> new BusinessException("GAME_NOT_FOUND",
                        HttpStatus.NOT_FOUND, "경기를 찾을 수 없습니다."));
        return GameDetailResponse.from(game);
    }

    @Transactional(readOnly = true)
    public List<SeatSectionResponse> getSeatSections(Long gameId) {
        Game game = gameRepository.findByGameId(gameId)
                .orElseThrow(() -> new BusinessException("GAME_NOT_FOUND",
                        HttpStatus.NOT_FOUND, "경기를 찾을 수 없습니다."));
        return seatSectionRepository
                .findByStadium_StadiumIdOrderByPriceDesc(game.getStadium().getStadiumId())
                .stream()
                .map(SeatSectionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<GameSeatResponse> getSeats(Long gameId, Long sectionId, String status) {
        if (!gameRepository.existsById(gameId)) {
            throw new BusinessException("GAME_NOT_FOUND",
                    HttpStatus.NOT_FOUND, "경기를 찾을 수 없습니다.");
        }
        GameSeatStatus seatStatus = parseSeatStatus(status);
        return gameSeatRepository.findDetailedByGameId(gameId, sectionId, seatStatus)
                .stream()
                .map(GameSeatResponse::from)
                .toList();
    }

    private GameStatus parseStatus(String status) {
        try {
            return GameStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("INVALID_STATUS", HttpStatus.BAD_REQUEST,
                    "유효하지 않은 경기 상태입니다: " + status);
        }
    }

    private GameSeatStatus parseSeatStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return GameSeatStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("INVALID_STATUS", HttpStatus.BAD_REQUEST,
                    "유효하지 않은 좌석 상태입니다: " + status);
        }
    }
}
