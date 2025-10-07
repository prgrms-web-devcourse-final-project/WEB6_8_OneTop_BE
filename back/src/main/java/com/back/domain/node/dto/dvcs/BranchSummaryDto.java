/**
 * [DTO-RES] 브랜치/커밋 요약 응답
 * - 브랜치/커밋 선택 UI, 핀 고정 UI에서 사용
 */
package com.back.domain.node.dto.dvcs;

import java.time.LocalDateTime;
import java.util.List;

public record BranchSummaryDto(
        Long branchId,
        Long baseLineId,
        String name,
        Long headCommitId,
        List<CommitSummary> commits
) {
    public record CommitSummary(
            Long commitId,
            Long parentCommitId,
            Long authorUserId,
            String message,
            LocalDateTime createdAt
    ) {}
}
