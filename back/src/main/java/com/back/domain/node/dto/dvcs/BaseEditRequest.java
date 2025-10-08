/**
 * [DTO-REQ] 베이스 편집 요청
 * - 특정 브랜치에서 ageYear 대상 콘텐츠를 패치로 반영
 */
package com.back.domain.node.dto.dvcs;

import com.back.domain.node.entity.NodeCategory;

public record BaseEditRequest(
        Long baseLineId,
        Long branchId,
        Integer ageYear,
        NodeCategory category,
        String situation,
        String decision,
        String optionsJson,
        String description,
        String contentHash,
        String message
) {}
