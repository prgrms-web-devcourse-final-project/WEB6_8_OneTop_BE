/**
 * [DTO] 트리 전체 조회 응답 모델 (Base/Decision 분리 리스트)
 *
 * 흐름 요약
 * - baseNodes : 사용자 소유 BaseNode 전부를 ageYear asc, id asc로 정렬해 반환
 * - decisionNodes : 사용자 소유 모든 DecisionLine의 노드를 라인별 정렬 조회 후 평탄화하여 반환
 */
package com.back.domain.node.dto;

import com.back.domain.node.dto.base.BaseNodeDto;
import com.back.domain.node.dto.decision.DecNodeDto;

import java.util.List;

public record TreeDto(
        List<BaseNodeDto> baseNodes,
        List<DecNodeDto> decisionNodes
) {}
