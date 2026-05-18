package com.baseball.auth.controller;

import com.baseball.auth.common.ApiResponse;
import com.baseball.auth.dto.LoginRequest;
import com.baseball.auth.dto.LoginResponse;
import com.baseball.auth.dto.SignupRequest;
import com.baseball.auth.dto.UserResponse;
import com.baseball.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<UserResponse>> signup(
            @Valid @RequestBody SignupRequest request) {
        UserResponse response = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, "회원가입이 완료되었습니다."));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request), "로그인 성공");
    }

    // JWT의 subject(userId)는 JwtAuthenticationFilter가 principal로 등록
    @GetMapping("/me")
    public ApiResponse<UserResponse> getMe(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(authService.getMe(userId));
    }
}
