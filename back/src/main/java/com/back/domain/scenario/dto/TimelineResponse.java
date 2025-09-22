package com.back.domain.scenario.dto;

import com.back.domain.node.entity.DecisionNode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 시나리오의 타임라인 정보를 담는 응답 DTO.
 * DecisionLine의 DecisionNode들을 시간순으로 정렬한 이벤트 목록을 제공합니다.
 * 비교 화면에서는 이 API를 두 번 호출하여 클라이언트에서 조합합니다.
 */

public record TimelineResponse(
        @Schema(description = "타임라인이 속한 시나리오 ID", example = "1001")
        Long scenarioId,

        @Schema(description = "시간순으로 정렬된 타임라인 이벤트 목록", example = "[{\\\"year\\\":2020,\\\"title\\\":\\\"창업 도전\\\"},{\\\"year\\\":2022,\\\"title\\\":\\\"해외 진출\\\"},{\\\"year\\\":2025,\\\"title\\\":\\\"상장 성공\\\"}]")
        List<TimelineEvent> events
) {
    public record TimelineEvent(
            @Schema(description = "이벤트 발생 연도", example = "2022")
            int year,

            @Schema(description = "AI가 생성한 이벤트 제목 (3-5단어)\", example = \"해외 진출 성공")
            String title
    ) {}

    /**
     * Scenario의 DecisionLine으로부터 TimelineResponse를 생성합니다.
     * @param scenarioId 시나리오 ID
     * @param decisionNodes DecisionLine의 DecisionNode 목록 (시간순 정렬됨)
     * @return TimelineResponse
     */

    public static TimelineResponse from(Long scenarioId, List<DecisionNode> decisionNodes) {
        throw new UnsupportedOperationException("구현 예정");
    }
}
