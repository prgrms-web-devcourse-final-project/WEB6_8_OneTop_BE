/**
 * [DTO-REQ] DecisionNode 생성 요청
 * - decisionLineId 없으면 새 라인 생성
 * - parentId 또는 baseNodeId 중 하나 사용
 */
package com.back.domain.node.dto;

import com.back.domain.node.entity.NodeCategory;

public record DecisionNodeCreateRequestDto(
        Long decisionLineId,
        Long parentId,
        Long baseNodeId,
        NodeCategory category,
        String situation,
        String decision,
        Integer ageYear
) {}
