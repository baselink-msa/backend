package com.baseball.waiting_room_service.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TokenResponse {
    private String ticketAccessToken;
    private Long expiresIn;
}