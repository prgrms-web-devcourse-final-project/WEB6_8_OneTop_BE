/**
 * [DTO-REQ] BaseLine의 특정 분기점(BaseNode)에서 DecisionNode 생성 요청
 * - category/situation/ageYear 미지정 시 pivot 값 상속
 */
package com.back.domain.node.dto;

import com.back.domain.node.entity.NodeCategory;

public record DecisionNodeFromBaseRequest(
        Long userId,
        Long baseLineId,
        Long pivotBaseNodeId,
        NodeCategory category,
        String situation,
        String decision,
        Integer ageYear
) {}
