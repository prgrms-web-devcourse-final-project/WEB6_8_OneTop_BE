package com.back.domain.scenario.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 시나리오 생성 요청 DTO.
 * DecisionLine을 기반으로 AI 시나리오 생성을 요청합니다.
 */

public record ScenarioCreateRequest(
        @Schema(description = "시나리오 생성의 기반이 될 대체선택 라인 ID", example = "1001")
        @NotNull(message = "대체선택 라인 ID는 필수입니다.")
        Long decisionLineId
) {
}
