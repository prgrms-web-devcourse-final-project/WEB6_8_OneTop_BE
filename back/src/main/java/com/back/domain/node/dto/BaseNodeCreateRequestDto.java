/**
 * [DTO-REQ] BaseNode 단건 생성 요청
 * - nodeKind는 BASE 권장
 */
package com.back.domain.node.dto;

import com.back.domain.node.entity.NodeCategory;
import com.back.domain.node.entity.NodeType;

public record BaseNodeCreateRequestDto(
        NodeType nodeKind,
        NodeCategory category,
        String situation,
        Integer ageYear
) {}
