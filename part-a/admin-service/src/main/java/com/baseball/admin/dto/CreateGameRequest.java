package com.baseball.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record CreateGameRequest(
        @NotBlank String homeTeamName,
        @NotBlank String awayTeamName,
        @NotNull Long stadiumId,
        @NotNull LocalDateTime gameStartTime,
        @NotNull LocalDateTime ticketOpenTime
) {
}
