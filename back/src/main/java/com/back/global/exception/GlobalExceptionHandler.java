package com.back.global.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 전역 예외 처리 핸들러.
 * 애플리케이션 전반에서 발생하는 예외를 중앙에서 처리하여
 * 일관된 에러 응답을 제공합니다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    protected ResponseEntity<ErrorResponse> handleApiException(ApiException e, HttpServletRequest request) {
        // 커스텀 비즈니스 예외 처리
        log.error("ApiException: {}", e.getMessage());
        return ResponseEntity
                .status(e.getErrorCode().getStatus())
                .body(ErrorResponse.of(e.getErrorCode().getStatus(), e.getErrorCode(), e.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    protected ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException e, HttpServletRequest request) {
        // @Valid 어노테이션을 사용한 유효성 검사 실패 예외 처리
        log.error("MethodArgumentNotValidException: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_INPUT_VALUE, e.getBindingResult().getAllErrors().get(0).getDefaultMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    protected ResponseEntity<ErrorResponse> handleException(Exception e, HttpServletRequest request) {
        // 모든 예상치 못한 예외 처리
        log.error("Exception: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_SERVER_ERROR, e.getMessage(), request.getRequestURI()));
    }
}