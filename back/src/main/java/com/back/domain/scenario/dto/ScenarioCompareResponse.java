package com.back.domain.scenario.dto;

import com.back.domain.scenario.entity.Scenario;
import com.back.domain.scenario.entity.SceneCompare;
import com.back.domain.scenario.entity.SceneType;
import com.back.domain.scenario.entity.Type;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**                                                                                                                                                                                                                  │ │
 * 두 시나리오 간의 비교 분석 결과를 담는 응답 DTO.                                                                                                                                                                  │ │
 * 기준 시나리오와 비교 시나리오의 5개 지표별 점수 비교와 AI 분석 결과를 제공합니다.                                                                                                                                 │ ││ │
 */

public record ScenarioCompareResponse(
        @Schema(description = "비교 기준이 되는 시나리오 ID", example = "1001")
        Long baseScenarioId,

        @Schema(description = "비교 대상 시나리오 ID", example = "1002")
        Long compareScenarioId,

        @Schema(description = "AI가 생성한 두 시나리오의 종합 비교 분석", example = "평행우주에서는 창업을 선택하여 더 높은 경제적 성과와 성취감을 얻었지만, 초기 불안정성과 스트레스가 증가했습니다. 전반적으로 위험을 감수한 만큼 더 큰 보상을 얻은 결과를 보여줍니다.")
        String overallAnalysis,

        @Schema(description = "5개 지표별 상세 비교 결과", example = "[{\"type\":\"경제\",\"baseScore\":65,\"compareScore\":85,\"analysis\":\"창업 성공으로 20점 향상\"}]")
        List<IndicatorComparison> indicators
) {

    public record IndicatorComparison(
            @Schema(description = "비교 지표 유형", example = "경제")
            Type type,

            @Schema(description = "기준 시나리오의 해당 지표 점수", example = "65")
            int baseScore,

            @Schema(description = "비교 시나리오의 해당 지표 점수", example = "85")
            int compareScore,

            @Schema(description = "해당 지표에 대한 AI 비교 분석", example = "창업 성공으로 인한 수익 증가와 자산 형성으로 경제 지표가 크게 향상되었습니다.")
            String analysis
    ) {}

    public static ScenarioCompareResponse from(
            Scenario baseScenario,
            Scenario compareScenario,
            List<SceneCompare> compareResults,
            List<SceneType> baseIndicators,
            List<SceneType> compareIndicators
    ) {
        throw new UnsupportedOperationException("구현 예정");
    }
}
