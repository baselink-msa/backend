package com.baseball.auth.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

// 비즈니스 규칙 위반 예외. GlobalExceptionHandler가 code/status를 응답에 매핑
@Getter
public class BusinessException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public BusinessException(String code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }
}
