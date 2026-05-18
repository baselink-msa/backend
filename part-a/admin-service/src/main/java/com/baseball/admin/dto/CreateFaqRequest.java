package com.baseball.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateFaqRequest(
        @NotBlank String category,
        @NotBlank String question,
        @NotBlank String answer,
        Boolean enabled
) {
}
