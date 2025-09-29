/**
 * [DTO-REQ] 결정 노드에서 세계선 포크(새 DecisionLine 생성)
 * - parentDecisionNodeId: 포크 기준이 되는 기존 결정 노드
 * - targetOptionIndex: 이 노드에서 새 라인에 적용할 선택지(0..2)
 * - keepUntilParent: true면 부모까지 복제(기본 true) — false면 부모 이전까지만 복제 후 새 노드부터 시작
 * - lineTitle: 새 라인에 붙일 식별용 메모(선택)
 */
package com.back.domain.node.dto.decision;

public record ForkFromDecisionRequest(
        Long userId,
        Long parentDecisionNodeId,
        Integer targetOptionIndex,
        Boolean keepUntilParent,
        String lineTitle
) {}
