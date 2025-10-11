package com.back.global.ai.exception;

import com.back.global.exception.ApiException;
import com.back.global.exception.ErrorCode;

/**
 * AI 서비스 관련 예외의 기본 클래스.
 * ApiException을 상속받아 GlobalExceptionHandler에서 일관된 예외 처리를 보장합니다.
 * 모든 AI 관련 예외는 이 클래스를 상속받아 구현됩니다.
 */
public class AiServiceException extends ApiException {

    public AiServiceException(ErrorCode errorCode) {
        super(errorCode);
    }

    public AiServiceException(ErrorCode errorCode, String customMessage) {
        super(errorCode, customMessage);
    }
}
