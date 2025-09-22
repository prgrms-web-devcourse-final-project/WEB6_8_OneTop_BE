/**
 * [DTO-RES] DecisionNode 응답
 * - background: AI 생성 설명(옵션)
 */
package com.back.domain.node.dto;

import com.back.domain.node.entity.NodeCategory;

public record DecLineDto(
        Long id,
        Long userId,
        String type,               // "DECISION"
        NodeCategory category,
        String situation,
        String decision,
        Integer ageYear,
        Long decisionLineId,
        Long parentId,
        Long baseNodeId,
        String background
) {

}
