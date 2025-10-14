package com.back.global.ai.dto.result;

import com.back.domain.scenario.entity.Type;

import java.util.List;
import java.util.Map;

/**
 * 새 시나리오 생성 결과 DTO
 * AI가 분석한 대안 선택 경로 결과와 베이스 시나리오 비교 분석을 포함합니다.
 */
public record DecisionScenarioResult(
        String job,
        String summary,
        String description,
        int total,  // 5개 지표 점수 합계
        String imagePrompt,
        Map<String, String> timelineTitles,
        List<Indicator> indicators, // AI 응답 구조와 일치 (배열)
        List<Comparison> comparisons // AI 응답 구조와 일치 (배열)
) {
    /**
     * AI 응답의 indicators 배열 요소
     */
    public record Indicator(
            String type,
            int point,
            String analysis
    ) {}

    /**
     * AI 응답의 comparisons 배열 요소
     */
    public record Comparison(
            String type,
            int baseScore,
            int newScore,
            String analysis
    ) {}

    /**
     * indicators 배열을 Map<Type, Integer>로 변환
     */
    public Map<Type, Integer> indicatorScores() {
        return indicators.stream()
                .collect(java.util.stream.Collectors.toMap(
                        ind -> Type.valueOf(ind.type),
                        Indicator::point
                ));
    }

    /**
     * indicators 배열을 Map<Type, String>로 변환
     */
    public Map<Type, String> indicatorAnalysis() {
        return indicators.stream()
                .collect(java.util.stream.Collectors.toMap(
                        ind -> Type.valueOf(ind.type),
                        Indicator::analysis
                ));
    }

    /**
     * comparisons 배열을 Map<String, String>로 변환
     */
    public Map<String, String> comparisonResults() {
        return comparisons.stream()
                .collect(java.util.stream.Collectors.toMap(
                        Comparison::type,
                        Comparison::analysis
                ));
    }
}
