package com.back.global.exception;

import lombok.Getter;

/**
 * 애플리케이션에서 발생하는 비즈니스 로직 관련 예외를 정의하는 커스텀 예외 클래스.
 * ErrorCode를 포함하여 구체적인 에러 정보와 HTTP 상태 코드를 제공합니다.
 */
@Getter
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    public ApiException(ErrorCode errorCode) {
        this(errorCode, errorCode.getMessage());
    }

    public ApiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
