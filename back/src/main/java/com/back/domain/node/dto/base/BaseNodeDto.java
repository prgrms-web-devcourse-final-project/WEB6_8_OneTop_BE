/**
 * [DTO-RES] BaseNode 응답(보강)
 * - currentVersionId와 effective* 필드를 추가해 버전 해석 결과를 노출
 */
package com.back.domain.node.dto.base;

import com.back.domain.node.entity.NodeCategory;
import java.util.List;

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
        Long altOpt2TargetDecisionId,
        String description,

        // 버전/해석 결과
        Long currentVersionId,
        NodeCategory effectiveCategory,
        String effectiveSituation,
        String effectiveDecision,
        List<String> effectiveOptions,
        String effectiveDescription
) {}
