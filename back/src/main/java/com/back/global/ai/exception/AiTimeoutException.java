package com.back.global.ai.exception;

import com.back.global.exception.ErrorCode;

/**
 * AI 요청 타임아웃 시 발생하는 예외
 */
public class AiTimeoutException extends AiServiceException {
    public AiTimeoutException() {
        super(ErrorCode.AI_REQUEST_TIMEOUT);
    }

    public AiTimeoutException(String customMessage) {
        super(ErrorCode.AI_REQUEST_TIMEOUT, customMessage);
    }
}
