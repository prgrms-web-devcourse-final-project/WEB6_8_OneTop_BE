/**
 * [DTO-REQ] 브랜치 생성/선택 요청
 * - BaseLine 기준으로 브랜치를 만들거나 선택하여 DecisionLine에 적용
 */
package com.back.domain.node.dto.dvcs;

public record BranchSelectRequest(
        Long baseLineId,
        String name,       // 새 브랜치명(없으면 선택만)
        Long useBranchId,  // 기존 브랜치 선택 시 지정
        Long decisionLineId // 적용 대상 라인(선택; 없으면 브랜치 생성만)
) {}
