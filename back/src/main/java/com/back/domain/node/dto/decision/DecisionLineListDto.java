/**
 * [DTO-RES] 결정 라인 목록(요약) 응답
 */
package com.back.domain.node.dto.decision;

import com.back.domain.node.entity.DecisionLineStatus;
import java.time.LocalDateTime;
import java.util.List;

public record DecisionLineListDto(
        List<LineSummary> lines
) {
    public record LineSummary(
            Long decisionLineId,
            Long baseLineId,
            DecisionLineStatus status,
            Integer nodeCount,
            Integer firstAge,
            Integer lastAge,
            LocalDateTime createdAt
    ) {}
}
