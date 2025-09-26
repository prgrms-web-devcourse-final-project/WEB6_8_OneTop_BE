package com.back.global.ai.dto.result;

import java.util.List;

/**
 * 베이스 시나리오 생성 결과 DTO
 * 사용자 현재 삶을 기반으로 생성된 기준점 시나리오 정보를 담습니다.
 */
public record BaseScenarioResult(
        String job,
        String summary,
        String description,
        List<String> timelineTitles,
        String baselineTitle
) {
}
