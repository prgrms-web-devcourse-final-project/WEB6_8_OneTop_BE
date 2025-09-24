/**
 * [DTO-REQ] 직전 DecisionNode(parent)에서 다음 DecisionNode 생성(슬림 계약)
 * - 라인은 부모로부터 해석하고, ageYear 미지정 시 다음 피벗 나이 자동 선택
 */
package com.back.domain.node.dto;

import com.back.domain.node.entity.NodeCategory;
import java.util.List;

public record DecisionNodeNextRequest(
        Long userId,
        Long parentDecisionNodeId,
        NodeCategory category,     // 미지정 시 parent.category 상속
        String situation,          // 미지정 시 parent.situation 상속
        Integer ageYear,           // null이면 다음 피벗 자동 선택
        List<String> options,      // 1~3개, null 가능
        Integer selectedIndex,     // 0..2, null 가능
        Integer parentOptionIndex  // 부모 옵션 인덱스(0..2), null 가능
) {}
