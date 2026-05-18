package com.baseball.auth.dto;

public record LoginResponse(
        String accessToken,
        UserResponse user
) {
}
