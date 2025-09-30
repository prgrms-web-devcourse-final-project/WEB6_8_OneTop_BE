package com.back.global.ai.exception;

import com.back.global.exception.ErrorCode;

/**
 * AI 응답 파싱 실패 시 발생하는 예외
 */
public class AiParsingException extends AiServiceException {
    public AiParsingException(String message) {
        super(ErrorCode.AI_RESPONSE_PARSING_ERROR, message);
    }

    // 기본 메세지
    public AiParsingException() {
        super(ErrorCode.AI_RESPONSE_PARSING_ERROR);
    }
}
