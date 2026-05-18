package com.baseball.auth.controller;

import com.baseball.auth.common.ApiResponse;
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
        body.put("service", "auth-service");
        body.put("status", "UP");
        body.put("timestamp", LocalDateTime.now().toString());
        return ApiResponse.ok(body);
    }
}
