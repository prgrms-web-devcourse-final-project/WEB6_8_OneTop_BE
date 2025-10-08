/**
 * [DTO-REQ] 팔로우 정책 변경 요청
 * - FOLLOW/PINNED/OVERRIDE 전환 및 커밋 고정 설정
 */
package com.back.domain.node.dto.dvcs;

import com.back.domain.node.entity.FollowPolicy;

public record FollowPolicyChangeRequest(
        Long decisionNodeId,
        FollowPolicy policy,
        Long pinnedCommitId // PINNED일 때만 사용
) {}
