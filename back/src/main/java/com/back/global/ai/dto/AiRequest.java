package com.back.global.ai.dto;

import java.util.Map;

/**
 * AI 서비스 요청 공통 DTO
 * 프롬프트와 생성 옵션을 포함한 AI 서비스 요청 객체입니다.
 */
public record AiRequest(
        String prompt,
        Map<String, Object> parameters, // 템플릿 변수용
        int maxTokens
) {
    public AiRequest(String prompt, Map<String, Object> parameters) {
        this(prompt, parameters, 2048);
    }
}
