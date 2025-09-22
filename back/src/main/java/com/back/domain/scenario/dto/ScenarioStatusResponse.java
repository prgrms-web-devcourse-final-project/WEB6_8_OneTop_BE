package com.back.domain.scenario.dto;

import com.back.domain.scenario.entity.ScenarioStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 시나리오 생성 상태 조회 응답 DTO.
 * 폴링 방식의 실시간 상태 확인을 위한 경량화된 응답 구조입니다.
 */

public record ScenarioStatusResponse(
        @Schema(description = "시나리오 ID", example = "1001")
        Long scenarioId,

        @Schema(description = "시나리오 생성 상태", example = "PROCESSING")
        ScenarioStatus status,

        @Schema(description = "상태별 안내 메시지", example = "AI가 대체선택 라인을 분석 중입니다...")
        String message
) {
}
