/**
 * [DTO-RES] 결정 라인 상세(라인 메타 + 노드 목록)
 * - nodes는 시간축(ageYear asc)으로 정렬
 */
package com.back.domain.node.dto.decision;

import com.back.domain.node.entity.DecisionLineStatus;
import java.util.List;

public record DecisionLineDetailDto(
        Long decisionLineId,
        Long userId,
        Long baseLineId,
        DecisionLineStatus status,
        List<DecNodeDto> nodes
) {}
