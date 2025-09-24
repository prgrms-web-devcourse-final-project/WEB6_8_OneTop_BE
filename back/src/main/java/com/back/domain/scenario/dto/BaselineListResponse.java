package com.back.domain.scenario.dto;

import com.back.domain.node.entity.BaseLine;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 베이스라인 목록 조회 응답 DTO.
 * 사용자의 현재 삶 시작점들을 목록으로 제공합니다.
 */

public record BaselineListResponse(
        @Schema(description = "베이스라인 ID", example = "1001")
        Long baselineId,

        @Schema(description = "베이스라인 제목", example = "대학 졸업 후 진로 선택")
        String title,

        @Schema(description = "베이스라인 관련 태그", example = "[\"교육\", \"진로\", \"취업\"]")
        List<String> tags,

        @Schema(description = "베이스라인 생성일", example = "2024-09-17T14:30:00")
        LocalDateTime createdDate
) {
    /**
     * BaseLine 엔티티로부터 BaselineListResponse를 생성합니다.
     * @param baseLine BaseLine 엔티티
     * @param tags 관련 태그 목록
     * @return BaselineListResponse
     */

    // TODO: 구현 필요
    public static BaselineListResponse from(BaseLine baseLine, List<String> tags) {
        throw new UnsupportedOperationException("구현 예정");
    }
}
