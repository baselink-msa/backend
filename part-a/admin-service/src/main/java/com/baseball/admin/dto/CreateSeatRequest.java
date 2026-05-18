package com.baseball.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateSeatRequest(
        @NotNull Long stadiumId,
        @NotNull Long sectionId,
        @NotBlank String seatRow,
        @NotBlank String seatNumber
) {
}
