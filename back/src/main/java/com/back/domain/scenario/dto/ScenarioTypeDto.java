package com.back.domain.scenario.dto;

import com.back.domain.scenario.entity.Type;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 시나리오 지표별 분석 결과 DTO.
 * 경제, 행복, 관계, 직업, 건강 각 지표의 점수와 상세 분석을 포함합니다.
 */

public record ScenarioTypeDto(
        @Schema(description = "시나리오 결과 지표 유형", example = "경제")
        Type type,

        @Schema(description = "시나리오 결과 지표 유형별 점수", example = "50")
        int point,

        @Schema(description = "시나리오 결과 지표 유형별 분석", example = "연구 성과로 인한 안정적인 수입과 지속적인 성장 기회를 확보했습니다.")
        String analysis
) {
}
