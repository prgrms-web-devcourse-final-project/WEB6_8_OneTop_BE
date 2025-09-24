/**
 * [DTO-RES] DecisionNode 응답
 * - options/selectedIndex/parentOptionIndex를 포함해 프론트 렌더 정보를 제공한다
 */
package com.back.domain.node.dto;

import com.back.domain.node.entity.NodeCategory;
import java.util.List;

public record DecLineDto(
        Long id,
        Long userId,
        String type,
        NodeCategory category,
        String situation,
        String decision,
        Integer ageYear,
        Long decisionLineId,
        Long parentId,
        Long baseNodeId,
        String background,
        List<String> options,
        Integer selectedIndex,
        Integer parentOptionIndex
) {}
