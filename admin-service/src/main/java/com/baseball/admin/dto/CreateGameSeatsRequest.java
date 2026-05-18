package com.baseball.admin.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record CreateGameSeatsRequest(
        @NotEmpty List<Long> seatIds,
        @NotNull @Positive Integer price
) {
}
