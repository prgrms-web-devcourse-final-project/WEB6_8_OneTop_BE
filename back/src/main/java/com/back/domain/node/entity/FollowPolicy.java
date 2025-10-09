/**
 * [ENTITY] FollowPolicy
 * - 노드가 베이스 변경을 따르는 방식(FOLLOW), 특정 커밋에 고정(PINNED), 라인 고유 버전 사용(OVERRIDE)을 나타냄
 */
package com.back.domain.node.entity;

public enum FollowPolicy {
    FOLLOW,
    PINNED,
    OVERRIDE
}
