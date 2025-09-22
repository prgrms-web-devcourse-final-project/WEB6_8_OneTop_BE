/**
 * [ENTITY] DecisionLine 진행 상태
 * - DRAFT: 작성중(취소 가능)
 * - COMPLETED: 작성 완료
 * - CANCELLED: 취소됨
 */
package com.back.domain.node.entity;

public enum DecisionLineStatus {
    DRAFT, COMPLETED, CANCELLED
}
