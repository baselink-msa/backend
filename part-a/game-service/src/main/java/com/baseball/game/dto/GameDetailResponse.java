package com.baseball.game.dto;

import com.baseball.game.domain.Game;

import java.time.LocalDateTime;

public record GameDetailResponse(
        Long gameId,
        String homeTeamName,
        String awayTeamName,
        StadiumResponse stadium,
        LocalDateTime gameStartTime,
        LocalDateTime ticketOpenTime,
        String status
) {
    public static GameDetailResponse from(Game game) {
        return new GameDetailResponse(
                game.getGameId(),
                game.getHomeTeamName(),
                game.getAwayTeamName(),
                StadiumResponse.from(game.getStadium()),
                game.getGameStartTime(),
                game.getTicketOpenTime(),
                game.getStatus().name()
        );
    }
}
