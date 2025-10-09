/**
 * [DTO-RES] DecisionNode 응답(보강 최종본)
 * - 정책/핀/가상(followPolicy/pinnedCommitId/virtual)과 effective* 해석값 노출
 * - 렌더 편의: childrenIds/root/pivot 링크 + 단일 패스 렌더용 힌트(renderPhase, incoming*)
 */
package com.back.domain.node.dto.decision;

import com.back.domain.node.entity.FollowPolicy;
import com.back.domain.node.entity.NodeCategory;

import java.util.List;

public record DecNodeDto(
        Long id,
        Long userId,
        String type,
        NodeCategory category,
        String situation,
        String decision,
        Integer ageYear,
        Long decisionLineId,
        Long parentId,
        Long baseNodeId,
        String background,
        List<String> options,
        Integer selectedIndex,
        Integer parentOptionIndex,
        String description,
        String aiNextSituation,
        String aiNextRecommendedOption,

        // 정책/핀/가상
        FollowPolicy followPolicy,
        Long pinnedCommitId,
        Boolean virtual,

        // 해석 결과(렌더 우선 사용; null이면 상단 원본 사용)
        NodeCategory effectiveCategory,
        String effectiveSituation,
        String effectiveDecision,
        List<String> effectiveOptions,
        String effectiveDescription,

        // 기본 렌더 편의
        List<Long> childrenIds,        // 이 DECISION의 자식들 id(시간순)
        Boolean root,                  // 라인 헤더면 true
        Long pivotLinkBaseNodeId,      // 베이스 분기 슬롯에서 올라온 첫 노드면 해당 BaseNode id
        Integer pivotSlotIndex,        // 0/1 (분기 슬롯 인덱스), 아니면 null

        // 단일 패스 렌더용 최소 힌트
        Integer renderPhase,           // 1=from-base 라인, 2..N=fork 깊이(부모 라인 +1)
        Long incomingFromId,           // 이 노드로 "들어오는" 에지의 from 노드 id(루트면 포크 원본, 아니면 parentId)
        String incomingEdgeType        // "normal" | "fork"
) {
    // === 호환 오버로드(기존 서비스 호출 유지) ===
    public DecNodeDto(
            Long id, Long userId, String type, NodeCategory category,
            String situation, String decision, Integer ageYear,
            Long decisionLineId, Long parentId, Long baseNodeId,
            String background, List<String> options, Integer selectedIndex,
            Integer parentOptionIndex, String description,
            String aiNextSituation, String aiNextRecommendedOption,
            FollowPolicy followPolicy, Long pinnedCommitId, Boolean virtual,
            NodeCategory effectiveCategory, String effectiveSituation, String effectiveDecision,
            List<String> effectiveOptions, String effectiveDescription
    ) {
        this(id, userId, type, category, situation, decision, ageYear,
                decisionLineId, parentId, baseNodeId, background, options, selectedIndex,
                parentOptionIndex, description, aiNextSituation, aiNextRecommendedOption,
                followPolicy, pinnedCommitId, virtual, effectiveCategory, effectiveSituation,
                effectiveDecision, effectiveOptions, effectiveDescription,
                null, null, null, null,
                null, null, null); // 새 필드는 null 기본값
    }
}
