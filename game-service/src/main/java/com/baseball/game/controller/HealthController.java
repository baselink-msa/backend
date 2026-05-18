package com.baseball.game.controller;

import com.baseball.game.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", "game-service");
        body.put("status", "UP");
        body.put("timestamp", LocalDateTime.now().toString());
        return ApiResponse.ok(body);
    }
}
