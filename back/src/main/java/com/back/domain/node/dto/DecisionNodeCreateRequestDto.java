/**
 * [DTO-REQ] DecisionNode 생성 요청
 * - 서비스 내부 매퍼용으로 사용되며 외부 API에서는 직접 받지 않는다
 */
package com.back.domain.node.dto;

import com.back.domain.node.entity.NodeCategory;
import java.util.List;

public record DecisionNodeCreateRequestDto(
        Long decisionLineId,
        Long parentId,
        Long baseNodeId,
        NodeCategory category,
        String situation,
        String decision,
        Integer ageYear,
        List<String> options,
        Integer selectedIndex,
        Integer parentOptionIndex
) {}
