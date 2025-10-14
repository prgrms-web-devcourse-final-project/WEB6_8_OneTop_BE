package com.back.global.ai.dto.result;

import com.back.domain.scenario.entity.Type;

import java.util.List;
import java.util.Map;

/**
 * 베이스 시나리오 생성 결과 DTO
 * 사용자 현재 삶을 기반으로 생성된 기준점 시나리오 정보를 담습니다.
 */
public record BaseScenarioResult(
        String job,
        String summary,
        String description,
        int total,  // 5개 지표 점수 합계
        Map<String, String> timelineTitles,
        String baselineTitle,
        List<Indicator> indicators // AI 응답 구조와 일치 (배열)
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
     * indicators 배열을 Map<Type, Integer>로 변환
     */
    public Map<Type, Integer> indicatorScores() {
        if (indicators == null) {
            return java.util.Collections.emptyMap();
        }
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
        if (indicators == null) {
            return java.util.Collections.emptyMap();
        }
        return indicators.stream()
                .collect(java.util.stream.Collectors.toMap(
                        ind -> Type.valueOf(ind.type),
                        Indicator::analysis
                ));
    }
}
