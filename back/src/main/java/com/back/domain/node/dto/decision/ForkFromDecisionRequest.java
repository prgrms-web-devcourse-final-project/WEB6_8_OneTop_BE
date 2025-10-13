/**
 * [DTO-REQ] 결정 노드에서 세계선 포크(옵션 교체 지원)
 * - parentDecisionNodeId 지점까지 타임라인 복제 후, 그 지점의 options/selectedIndex를 교체 가능
 * - 호환: 이전 keepUntilParent/lineTitle 형태로 생성되던 코드가 있으면 아래 보조 생성자가 흡수
 */
package com.back.domain.node.dto.decision;

import com.back.domain.node.entity.NodeCategory;

import java.util.List;

public record ForkFromDecisionRequest(
        Long userId,
        Long parentDecisionNodeId,
        Integer targetOptionIndex,   // 0..2 (입력 옵션이 없을 때 강제 선택으로 사용)
        List<String> options,        // 선택지 교체(0~3개; 보통 1~3)
        Integer selectedIndex,       // null이면 단일 옵션 시 0으로 정규화
        NodeCategory category,       // 선택(교체)
        String situation,            // 선택(교체)
        String description           // 선택(교체)
) {
    // 구버전 호환(keepUntilParent/lineTitle을 받던 생성 시그니처 흡수)
    public ForkFromDecisionRequest(Long userId,
                                   Long parentDecisionNodeId,
                                   Integer targetOptionIndex,
                                   Boolean keepUntilParent,
                                   String lineTitle) {
        this(userId, parentDecisionNodeId, targetOptionIndex,
                null, null, null, null, null);
    }
}
