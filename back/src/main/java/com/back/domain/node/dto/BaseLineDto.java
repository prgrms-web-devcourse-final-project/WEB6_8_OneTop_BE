/**
 * [DTO-RES] BaseNode 응답
 * - 프론트 전송 전용: 최소 필드만 노출
 */
package com.back.domain.node.dto;

import com.back.domain.node.entity.NodeCategory;

public record BaseLineDto(
        Long id,
        Long userId,
        String type,               // "BASE"
        NodeCategory category,
        String situation,
        String decision,
        Integer ageYear,
        Long baseLineId,
        Long parentId
) {

}
