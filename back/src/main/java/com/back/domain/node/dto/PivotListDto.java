/**
 * [DTO-RES] 중간 분기점(BaseNode) 목록
 * - 헤더/꼬리 제외 인덱스만 반환 → 사용자 선택용
 */
package com.back.domain.node.dto;

import com.back.domain.node.entity.NodeCategory;
import java.util.List;

public record PivotListDto(
        Long baseLineId,
        List<PivotDto> pivots
) {
    public record PivotDto(
            Integer index,
            Long baseNodeId,
            NodeCategory category,
            String situation,
            Integer ageYear
    ) {}
}
