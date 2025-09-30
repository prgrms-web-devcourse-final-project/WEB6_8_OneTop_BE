package com.back.domain.scenario.dto;

import com.back.domain.scenario.entity.Scenario;
import com.back.domain.scenario.entity.SceneType;
import com.back.domain.scenario.entity.ScenarioStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 시나리오 상세 조회 응답 DTO.
 * AI가 생성한 시나리오의 모든 상세 정보와 5개 지표 분석 결과를 포함합니다.
 */

public record ScenarioDetailResponse(
        @Schema(description = "시나리오 ID", example = "1001")
        Long scenarioId,

        @Schema(description = "시나리오 생성 상태", example = "COMPLETED")
        ScenarioStatus status,

        @Schema(description = "시나리오 최종 직업", example = "AI 연구원")
        String job,

        @Schema(description = "시나리오 지표 점수 총합", example = "500")
        int total,

        @Schema(description = "시나리오 결과 요약", example = "당신은 성공적인 AI 연구자가 되어 학계와 업계에서 인정받으며, 균형 잡힌 삶을 살고 있습니다.")
        String summary,

        @Schema(description = "시나리오 결과 상세", example = "10년 후, 당신은 세계적으로 인정받는 AI 연구소의 수석 연구원이 되었습니다. 혁신적인 연구 성과로 다수의 논문을 발표하며, 안정적인 수입과 함께 의미 있는 일을 하고 있습니다. 또한 규칙적인 운동과 취미 생활로 건강하고 행복한 일상을 유지하고 있습니다.")
        String description,

        @Schema(description = "시나리오 결과 이미지 URL", example = "https://example.com/scenario-images/successful-researcher.jpg")
        String img,

        @Schema(description = "시나리오 생성일자", example = "2024-01-15T14:30:00")
        LocalDateTime createdDate,

        @Schema(description = "시나리오 결과 지표 정보", example = "[{\"type\":\"경제\",\"point\":85,\"analysis\":\"안정적인 연구직 수입\"}, {\"type\":\"행복\",\"point\":90,\"analysis\":\"의미 있는 일을 통한 성취감\"}]")
        List<ScenarioTypeDto> indicators
) {
    public static ScenarioDetailResponse from(Scenario scenario, List<SceneType> sceneTypes) {
        List<ScenarioTypeDto> indicators = sceneTypes.stream()
                .map(st -> new ScenarioTypeDto(st.getType(), st.getPoint(), st.getAnalysis()))
                .toList();

        return new ScenarioDetailResponse(
                scenario.getId(),
                scenario.getStatus(),
                scenario.getJob(),
                scenario.getTotal(),
                scenario.getSummary(),
                scenario.getDescription(),
                scenario.getImg(),
                scenario.getCreatedDate(),
                indicators
        );
    }
}
