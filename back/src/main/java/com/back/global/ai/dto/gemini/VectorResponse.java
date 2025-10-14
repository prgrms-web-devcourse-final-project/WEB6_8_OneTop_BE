package com.back.global.ai.dto.gemini;

public record VectorResponse(
        String situation,
        String recommendedOption
) {}