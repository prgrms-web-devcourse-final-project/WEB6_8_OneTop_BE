/**
 * [DTO] 트리 전체 조회 응답 모델 (Base/Decision 분리 리스트)
 */
package com.back.domain.node.dto;

import java.util.List;

public record TreeDto(
        List<BaseLineDto> baseNodes,
        List<DecLineDto> decisionNodes
) {}
