package com.baseball.auth.common;

import com.fasterxml.jackson.annotation.JsonInclude;

// 공통 응답 포맷
//  성공: { success:true, data:{...}, message:"..." }
//  실패: { success:false, error:{ code:"...", message:"..." } }
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        T data,
        String message,
        ErrorBody error
) {
    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(true, data, message, null);
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    public static ApiResponse<Void> error(String code, String message) {
        return new ApiResponse<>(false, null, null, new ErrorBody(code, message));
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorBody(String code, String message) {
    }
}
