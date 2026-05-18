package com.baseball.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateSeatSectionRequest(
        @NotNull Long stadiumId,
        @NotBlank String sectionName,
        @NotNull @Positive Integer price
) {
}
