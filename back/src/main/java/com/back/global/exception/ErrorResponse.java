package com.back.global.exception;

import lombok.Builder;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;

/**
 * API 에러 응답을 위한 DTO 클래스.
 * RFC 7807 Problem Details for HTTP APIs 표준을 따르며,
 * 에러 발생 시 클라이언트에게 상세한 에러 정보를 제공합니다.
 */
@Getter
@Builder
public class ErrorResponse {

    private final LocalDateTime timestamp = LocalDateTime.now();
    private final int status;
    private final String error;
    private final String code;
    private final String message;
    private final String path;

    public static ErrorResponse of(HttpStatus status, ErrorCode errorCode, String path) {
        // ErrorCode와 경로를 사용하여 ErrorResponse 객체를 생성
        return ErrorResponse.builder()
                .status(status.value())
                .error(status.getReasonPhrase())
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .path(path)
                .build();
    }

    public static ErrorResponse of(HttpStatus status, ErrorCode errorCode, String message, String path) {
        // ErrorCode, 메시지, 경로를 사용하여 ErrorResponse 객체를 생성
        return ErrorResponse.builder()
                .status(status.value())
                .error(status.getReasonPhrase())
                .code(errorCode.getCode())
                .message(message)
                .path(path)
                .build();
    }
}