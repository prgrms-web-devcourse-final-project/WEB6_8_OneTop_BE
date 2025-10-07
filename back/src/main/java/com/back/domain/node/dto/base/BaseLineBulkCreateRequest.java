/**
 * [DTO-REQ] BaseLine을 라인 단위로 일괄 생성 요청(헤더~꼬리까지)
 * - nodes: 0=헤더, 마지막=꼬리, 사이가 피벗(분기점)
 */
package com.back.domain.node.dto.base;

import com.back.domain.node.entity.NodeCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.List;

public record BaseLineBulkCreateRequest(
        Long userId,
        String title,
        List<@Valid BaseNodePayload> nodes
) {
    public record BaseNodePayload(
            NodeCategory category,
            String situation,
            String decision,
            @Min(1)
            @Max(120)
            Integer ageYear,
            String description
    ) {}
}
