package com.back.global.ai.dto.gemini;

import java.util.List;

/**
 * Gemini API 응답 DTO
 * Google Gemini API의 응답 JSON을 매핑하는 record 객체입니다.
 */
public record GeminiResponse(
    List<Candidate> candidates,
    PromptFeedback promptFeedback
) {
    public record Candidate(
        Content content,
        String finishReason,
        int index,
        List<SafetyRating> safetyRatings
    ) {}

    public record Content(
        List<Part> parts,
        String role
    ) {}

    public record Part(
        String text
    ) {}

    public record PromptFeedback(
        List<SafetyRating> safetyRatings
    ) {}

    public record SafetyRating(
        String category,
        String probability
    ) {}
}
