package com.back.global.ai.dto;

import java.util.List;

/**
 * 베이스 시나리오 생성 결과 DTO
 */
public record BaseScenarioResult(
        String job,
        String summary,
        String description,
        List<String> timelineTitles,
        String baselineTitle
) {
}
