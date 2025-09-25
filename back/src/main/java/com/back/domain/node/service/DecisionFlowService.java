/**
 * DecisionFlowService
 * - from-base: 피벗 해석 + 분기 텍스트 반영(1~2개; 단일 옵션은 선택 슬롯만) + 선택 슬롯 링크 + 첫 결정 생성
 * - next     : 부모 기준 다음 피벗/나이 해석 + 매칭 + 연속 결정 생성(옵션 1~3개)
 * - cancel/complete: 라인 상태 전이
 */
package com.back.domain.node.service;

import com.back.domain.node.dto.*;
import com.back.domain.node.entity.*;
import com.back.domain.node.mapper.NodeMappers;
import com.back.domain.node.repository.BaseNodeRepository;
import com.back.domain.node.repository.DecisionLineRepository;
import com.back.domain.node.repository.DecisionNodeRepository;
import com.back.global.exception.ApiException;
import com.back.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
class DecisionFlowService {

    private final DecisionLineRepository decisionLineRepository;
    private final DecisionNodeRepository decisionNodeRepository;
    private final BaseNodeRepository baseNodeRepository;
    private final NodeDomainSupport support;

    // 가장 중요한: from-base 서버 해석(옵션 1~2개 허용; 단일 옵션은 선택 슬롯에만 반영)
    public DecLineDto createDecisionNodeFromBase(DecisionNodeFromBaseRequest request) {
        if (request == null || request.baseLineId() == null)
            throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "baseLineId is required");

        // 피벗 해석
        List<BaseNode> ordered = support.getOrderedBaseNodes(request.baseLineId());
        int pivotAge = support.resolvePivotAge(request.pivotOrd(), request.pivotAge(),
                support.allowedPivotAges(ordered));
        BaseNode pivot = support.findBaseNodeByAge(ordered, pivotAge);

        int sel = support.requireAltIndex(request.selectedAltIndex());

        // 이미 링크된 슬롯은 시작 불가
        Long chosenTarget = (sel == 0) ? pivot.getAltOpt1TargetDecisionId() : pivot.getAltOpt2TargetDecisionId();
        if (chosenTarget != null) throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "branch slot already linked");

        // FromBase 옵션: 1~2개만 허용(단일 옵션은 선택 슬롯에만 반영)
        List<String> opts = request.options();
        Integer selectedIndex = request.selectedIndex();
        if (opts != null && !opts.isEmpty()) {
            support.validateOptionsForFromBase(opts, selectedIndex);
            support.upsertPivotAltTextsForFromBase(pivot, opts, sel);
            baseNodeRepository.save(pivot);
        }

        // 최종 decision 텍스트 결정
        String chosenNow = (sel == 0) ? pivot.getAltOpt1() : pivot.getAltOpt2();
        String finalDecision =
                (opts != null && !opts.isEmpty() && selectedIndex != null
                        && selectedIndex >= 0 && selectedIndex < opts.size())
                        ? opts.get(selectedIndex)
                        : (opts != null && opts.size() == 1 ? opts.get(0) : chosenNow);

        if (finalDecision == null || finalDecision.isBlank())
            throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "empty branch slot and no options");

        // 라인 생성
        DecisionLine line = decisionLineRepository.save(
                DecisionLine.builder()
                        .user(pivot.getUser())
                        .baseLine(pivot.getBaseLine())
                        .status(DecisionLineStatus.DRAFT)
                        .build()
        );

        String situation = (request.situation() != null) ? request.situation() : pivot.getSituation();
        String background = support.resolveBackground(situation);

        NodeMappers.DecisionNodeCtxMapper mapper =
                new NodeMappers.DecisionNodeCtxMapper(pivot.getUser(), line, null, pivot, background);

        // 단일 옵션 & selectedIndex 미지정 시 0으로 기록(프론트 편의)
        Integer normalizedSelected = (opts != null && opts.size() == 1 && selectedIndex == null) ? 0 : selectedIndex;

        DecisionNodeCreateRequestDto createReq = new DecisionNodeCreateRequestDto(
                line.getId(), null, pivot.getId(),
                request.category() != null ? request.category() : pivot.getCategory(),
                situation, finalDecision, pivotAge,
                opts, normalizedSelected, null
        );

        DecisionNode saved = decisionNodeRepository.save(mapper.toEntity(createReq));

        // 선택된 슬롯만 링크
        if (sel == 0) pivot.setAltOpt1TargetDecisionId(saved.getId());
        else pivot.setAltOpt2TargetDecisionId(saved.getId());
        baseNodeRepository.save(pivot);

        return mapper.toResponse(saved);
    }

    // 가장 중요한: next 서버 해석(부모 기준 라인/다음 피벗/베이스 매칭 결정)
    public DecLineDto createDecisionNodeNext(DecisionNodeNextRequest request) {
        if (request == null || request.parentDecisionNodeId() == null)
            throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "parentDecisionNodeId is required");

        DecisionNode parent = decisionNodeRepository.findById(request.parentDecisionNodeId())
                .orElseThrow(() -> new ApiException(ErrorCode.NODE_NOT_FOUND, "Parent DecisionNode not found: " + request.parentDecisionNodeId()));
        DecisionLine line = parent.getDecisionLine();
        if (line.getStatus() == DecisionLineStatus.COMPLETED || line.getStatus() == DecisionLineStatus.CANCELLED)
            throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "line is locked");

        List<BaseNode> ordered = support.getOrderedBaseNodes(line.getBaseLine().getId());
        int nextAge = support.resolveNextAge(request.ageYear(), parent.getAgeYear(),
                support.allowedPivotAges(ordered));
        support.ensureAgeVacant(line, nextAge);
        BaseNode matchedBase = support.findBaseNodeByAge(ordered, nextAge);

        if (request.options() != null && !request.options().isEmpty()) {
            // Next는 1~3개 허용(기존 규칙)
            support.validateOptions(request.options(), request.selectedIndex(),
                    request.selectedIndex() != null ? request.options().get(request.selectedIndex()) : null);
        }

        String situation = (request.situation() != null) ? request.situation() : parent.getSituation();
        String background = support.resolveBackground(situation);

        NodeMappers.DecisionNodeCtxMapper mapper =
                new NodeMappers.DecisionNodeCtxMapper(parent.getUser(), line, parent, matchedBase, background);

        String finalDecision =
                (request.options() != null && !request.options().isEmpty()
                        && request.selectedIndex() != null
                        && request.selectedIndex() >= 0
                        && request.selectedIndex() < request.options().size())
                        ? request.options().get(request.selectedIndex())
                        : request.situation();

        DecisionNodeCreateRequestDto createReq = new DecisionNodeCreateRequestDto(
                line.getId(), parent.getId(), matchedBase != null ? matchedBase.getId() : null,
                request.category() != null ? request.category() : parent.getCategory(),
                situation, finalDecision, nextAge,
                request.options(), request.selectedIndex(), request.parentOptionIndex()
        );

        DecisionNode saved = decisionNodeRepository.save(mapper.toEntity(createReq));
        return mapper.toResponse(saved);
    }

    // 라인 취소
    public DecisionLineLifecycleDto cancelDecisionLine(Long decisionLineId) {
        DecisionLine line = support.requireDecisionLine(decisionLineId);
        try { line.cancel(); } catch (RuntimeException e) { throw support.mapDomainToApi(e); }
        decisionLineRepository.save(line);
        return new DecisionLineLifecycleDto(line.getId(), line.getStatus());
    }

    // 라인 완료
    public DecisionLineLifecycleDto completeDecisionLine(Long decisionLineId) {
        DecisionLine line = support.requireDecisionLine(decisionLineId);
        try { line.complete(); } catch (RuntimeException e) { throw support.mapDomainToApi(e); }
        decisionLineRepository.save(line);
        return new DecisionLineLifecycleDto(line.getId(), line.getStatus());
    }
}
