/**
 * [DTO-RES] 결정 라인 라이프사이클 상태 응답
 * - 취소/완료 후 상태 반환
 */
package com.back.domain.node.dto;

import com.back.domain.node.entity.DecisionLineStatus;

public record DecisionLineLifecycleDto(
        Long decisionLineId,
        DecisionLineStatus status
) {}
