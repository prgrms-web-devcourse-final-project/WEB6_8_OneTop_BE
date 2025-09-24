/**
 * [DTO-RES] BaseNode 응답
 * - 고정 선택과 분기 2칸 및 각 타겟 링크를 포함한다
 */
package com.back.domain.node.dto;

import com.back.domain.node.entity.NodeCategory;

public record BaseNodeDto(
        Long id,
        Long userId,
        String type,
        NodeCategory category,
        String situation,
        String decision,
        Integer ageYear,
        Long baseLineId,
        Long parentId,
        String title,
        String fixedChoice,
        String altOpt1,
        String altOpt2,
        Long altOpt1TargetDecisionId,
        Long altOpt2TargetDecisionId
) {}
