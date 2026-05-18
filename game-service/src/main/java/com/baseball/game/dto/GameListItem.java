package com.baseball.game.dto;

import com.baseball.game.domain.Game;

import java.time.LocalDateTime;

public record GameListItem(
        Long gameId,
        String homeTeamName,
        String awayTeamName,
        String stadiumName,
        LocalDateTime gameStartTime,
        LocalDateTime ticketOpenTime,
        String status
) {
    public static GameListItem from(Game game) {
        return new GameListItem(
                game.getGameId(),
                game.getHomeTeamName(),
                game.getAwayTeamName(),
                game.getStadium().getName(),
                game.getGameStartTime(),
                game.getTicketOpenTime(),
                game.getStatus().name()
        );
    }
}
