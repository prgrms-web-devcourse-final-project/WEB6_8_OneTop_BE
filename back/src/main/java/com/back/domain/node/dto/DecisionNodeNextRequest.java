/**
 * [DTO-REQ] 직전 DecisionNode(parent)에서 다음 DecisionNode 생성
 * - ageYear 미지정 시 자동으로 다음 피벗 나이 선택
 */
package com.back.domain.node.dto;

import com.back.domain.node.entity.NodeCategory;

public record DecisionNodeNextRequest(
        Long userId,
        Long parentDecisionNodeId,
        Long decisionLineId,
        NodeCategory category,
        String situation,
        String decision,
        Integer ageYear
) {}
