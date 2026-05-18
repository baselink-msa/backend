package com.baseball.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateMenuRequest(
        @NotBlank String name,
        @NotNull @Positive Integer price,
        Boolean available
) {
}
