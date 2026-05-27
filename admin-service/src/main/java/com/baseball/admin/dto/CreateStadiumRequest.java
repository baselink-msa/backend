package com.baseball.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateStadiumRequest(
        @NotBlank String name,
        @NotBlank String location,
        @NotNull Integer capacity
) {
}
