package com.back.global.ai.dto.gemini;

import java.util.List;

/**
 * Gemini API 응답 DTO
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
