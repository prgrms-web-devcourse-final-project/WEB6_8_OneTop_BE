package com.back.global.ai.dto;

import java.time.LocalDateTime;

/**
 * AI 서비스 응답 DTO
 */
public record AiResponse(
        String content, // AI 생성 내용
        boolean success,
        String errorMessage,
        LocalDateTime timestamp
) {
}
