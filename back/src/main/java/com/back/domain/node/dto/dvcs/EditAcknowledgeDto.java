/**
 * [DTO-RES] 편집 결과 공통 응답
 * - 편집/승격/정책 변경/브랜치 선택 후 최소 확인용 결과를 반환
 */
package com.back.domain.node.dto.dvcs;

public record EditAcknowledgeDto(
        boolean success,
        String message,
        Long affectedId
) {}
