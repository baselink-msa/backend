package com.baseball.auth.dto;

import com.baseball.auth.domain.User;

public record UserResponse(
        Long userId,
        String email,
        String name,
        String role,
        String status
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getUserId(),
                user.getEmail(),
                user.getName(),
                user.getRole().name(),
                user.getStatus().name()
        );
    }
}
