/**
 * [DTO-REQ] BaseLine 피벗에서 첫 결정 생성 요청(슬림 계약 + 옵션 지원)
 * - 서버가 baseLineId + (pivotOrd | pivotAge)로 피벗 노드를 해석하고, selectedAltIndex(0/1) 분기 슬롯을 링크한다
 */
package com.back.domain.node.dto.decision;

import com.back.domain.node.entity.NodeCategory;
import java.util.List;

public record DecisionNodeFromBaseRequest(
        Long userId,
        Long baseLineId,
        Integer pivotOrd,          // 피벗 순번(중간 노드 기준, 0부터) — null이면 pivotAge 사용
        Integer pivotAge,          // 피벗 나이 — null이면 pivotOrd 사용
        Integer selectedAltIndex,  // 0 또는 1
        NodeCategory category,     // 미지정 시 pivot.category 상속
        String situation,          // 미지정 시 pivot.situation 상속
        List<String> options,      // 1~3개, null 가능(첫 결정 노드도 옵션 보유 가능)
        Integer selectedIndex,      // 0..2, null 가능(주어지면 decision과 일치)
        String description
) {}
