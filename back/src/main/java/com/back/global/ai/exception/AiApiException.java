package com.back.global.ai.exception;

import com.back.global.exception.ErrorCode;

/**
 * AI API 호출 실패 시 발생하는 예외
 */
public class AiApiException extends AiServiceException {
    public AiApiException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
