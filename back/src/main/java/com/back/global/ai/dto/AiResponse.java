package com.back.global.ai.dto;

import java.time.LocalDateTime;

/**
 * AI 서비스 응답 공통 DTO
 * AI 서비스 응답 결과와 메타데이터를 포함하는 응답 객체입니다.
 */
public record AiResponse(
        String content, // AI 생성 내용
        boolean success,
        String errorMessage,
        LocalDateTime timestamp
) {
}
