package com.back.global.ai.exception;

import com.back.global.exception.ErrorCode;

/**
 * AI 서비스 관련 예외의 기본 클래스.
 * 모든 AI 관련 예외는 이 클래스를 상속받아 구현됩니다.
 */
public class AiServiceException extends RuntimeException {
    private final ErrorCode errorCode;

    public AiServiceException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public AiServiceException(ErrorCode errorCode, String customMessage) {
        super(customMessage);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
