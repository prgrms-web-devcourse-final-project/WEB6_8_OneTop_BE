/**
 * [DTO-RES] BaseLine 일괄 생성 응답
 * - 생성된 BaseLine id와 각 노드의 id/인덱스 반환
 */
package com.back.domain.node.dto;

import java.util.List;

public record BaseLineBulkCreateResponse(
        Long baseLineId,
        List<CreatedNode> nodes
) {
    public record CreatedNode(
            Integer index,
            Long nodeId
    ) {}
}
