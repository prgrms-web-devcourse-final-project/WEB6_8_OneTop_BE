/**
 * [DTO-REQ] 결정 노드 편집 요청
 * - 이 라인만 반영(OVERRIDE) 또는 업스트림 승격(브랜치 커밋) 중 하나를 수행
 */
package com.back.domain.node.dto.dvcs;

import com.back.domain.node.entity.NodeCategory;

public record DecisionEditRequest(
        Long decisionNodeId,
        boolean promoteToBase,   // true면 업스트림 승격, false면 OVERRIDE
        NodeCategory category,
        String situation,
        String decision,
        String optionsJson,
        String description,
        String contentHash,
        String message          // 승격 시 커밋 메시지
) {}
