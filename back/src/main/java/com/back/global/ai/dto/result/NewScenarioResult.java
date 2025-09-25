package com.back.global.ai.dto.result;

import com.back.domain.scenario.entity.Type;

import java.util.List;
import java.util.Map;

/**
 * 새 시나리오 생성 결과 DTO
 */
public record NewScenarioResult(
        String job,
        String summary,
        String description,
        String imagePrompt,
        List<String> timelineTitles,
        Map<Type, Integer> indicatorScores, // 각 지표별 점수
        Map<Type, String> indicatorAnalysis, // 각 지표별 분석 내용
        Map<String, String> comparisonResults // 비교 분석 결과
) {
}
