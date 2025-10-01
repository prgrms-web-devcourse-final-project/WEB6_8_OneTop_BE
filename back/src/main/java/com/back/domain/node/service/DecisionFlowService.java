/**
 * DecisionFlowService
 * - from-base: 피벗 해석 + 분기 텍스트 반영(1~2개; 단일 옵션은 선택 슬롯만) + 선택 슬롯 링크 + 첫 결정 생성
 * - next     : 부모 기준 다음 피벗/나이 해석 + 매칭 + 연속 결정 생성(옵션 1~3개)
 * - cancel/complete: 라인 상태 전이
 */
package com.back.domain.node.service;

import com.back.domain.node.dto.decision.*;
import com.back.domain.node.entity.BaseNode;
import com.back.domain.node.entity.DecisionLine;
import com.back.domain.node.entity.DecisionLineStatus;
import com.back.domain.node.entity.DecisionNode;
import com.back.domain.node.mapper.NodeMappers;
import com.back.domain.node.repository.BaseNodeRepository;
import com.back.domain.node.repository.DecisionLineRepository;
import com.back.domain.node.repository.DecisionNodeRepository;
import com.back.global.ai.vector.AIVectorService;
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
    private final AIVectorService aiVectorService;

    // 가장 중요한: from-base 서버 해석(옵션 1~2개 허용; 단일 옵션은 선택 슬롯에만 반영)
    public DecNodeDto createDecisionNodeFromBase(DecisionNodeFromBaseRequest request) {
        if (request == null || request.baseLineId() == null)
            throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "baseLineId is required");

        // 피벗 해석
        List<BaseNode> ordered = support.getOrderedBaseNodes(request.baseLineId());
        int pivotAge = support.resolvePivotAge(request.pivotOrd(), request.pivotAge(),
                support.allowedPivotAges(ordered));
        BaseNode pivot = support.findBaseNodeByAge(ordered, pivotAge);

        support.ensureOwnerOfBaseLine(request.userId(), pivot.getBaseLine());

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
                opts, normalizedSelected, null, request.description()
        );

        DecisionNode saved = decisionNodeRepository.save(mapper.toEntity(createReq));

        int updated = (sel == 0)
                ? baseNodeRepository.linkAlt1IfEmpty(pivot.getId(), saved.getId())
                : baseNodeRepository.linkAlt2IfEmpty(pivot.getId(), saved.getId());

        if (updated == 0) {
            throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "branch slot was taken by another request");
        }
        DecNodeDto baseDto = mapper.toResponse(saved);

        // 여기서 AI 힌트 주입 (응답 직전 동기)
        List<DecisionNode> orderedList = decisionNodeRepository
                .findByDecisionLine_IdOrderByAgeYearAscIdAsc(baseDto.decisionLineId());
        var hint = aiVectorService.generateNextHint(baseDto.userId(), baseDto.decisionLineId(), orderedList);

        return new DecNodeDto(
                baseDto.id(),
                baseDto.userId(),
                baseDto.type(),
                baseDto.category(),
                baseDto.situation(),
                baseDto.decision(),
                baseDto.ageYear(),
                baseDto.decisionLineId(),
                baseDto.parentId(),
                baseDto.baseNodeId(),
                baseDto.background(),
                baseDto.options(),
                baseDto.selectedIndex(),
                baseDto.parentOptionIndex(),
                baseDto.description(),
                hint.aiNextSituation(),
                hint.aiNextRecommendedOption()
        );
    }

    // 가장 중요한: next 서버 해석(부모 기준 라인/다음 피벗/베이스 매칭 결정)
    public DecNodeDto createDecisionNodeNext(DecisionNodeNextRequest request) {
        if (request == null || request.parentDecisionNodeId() == null)
            throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "parentDecisionNodeId is required");

        DecisionNode parent = decisionNodeRepository.findById(request.parentDecisionNodeId())
                .orElseThrow(() -> new ApiException(ErrorCode.NODE_NOT_FOUND, "Parent DecisionNode not found: " + request.parentDecisionNodeId()));
        DecisionLine line = parent.getDecisionLine();

        support.ensureOwnerOfDecisionLine(request.userId(), line);

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
                , request.description()
        );

        DecisionNode saved = decisionNodeRepository.save(mapper.toEntity(createReq));
        DecNodeDto baseDto = mapper.toResponse(saved);

        // AI 힌트 주입 (응답 직전 동기)
        List<DecisionNode> ordered_decision = decisionNodeRepository
                .findByDecisionLine_IdOrderByAgeYearAscIdAsc(baseDto.decisionLineId());
        var hint = aiVectorService.generateNextHint(baseDto.userId(), baseDto.decisionLineId(), ordered_decision);

        return new DecNodeDto(
                baseDto.id(),
                baseDto.userId(),
                baseDto.type(),
                baseDto.category(),
                baseDto.situation(),
                baseDto.decision(),
                baseDto.ageYear(),
                baseDto.decisionLineId(),
                baseDto.parentId(),
                baseDto.baseNodeId(),
                baseDto.background(),
                baseDto.options(),
                baseDto.selectedIndex(),
                baseDto.parentOptionIndex(),
                baseDto.description(),
                hint.aiNextSituation(),
                hint.aiNextRecommendedOption()
        );
    }

    // 라인 취소
    public DecisionLineLifecycleDto cancelDecisionLine(Long decisionLineId) {
        DecisionLine line = support.requireDecisionLine(decisionLineId);
        try {
            line.cancel();
        } catch (RuntimeException e) {
            throw support.mapDomainToApi(e);
        }
        decisionLineRepository.save(line);

        // 가장 많이 사용하는 호출: 시작 슬롯 원복(CAS 해제, 실패해도 무해)
        decisionNodeRepository.findFirstByDecisionLine_IdOrderByAgeYearAscIdAsc(line.getId())
                .ifPresent(first -> {
                    BaseNode anchor = first.getBaseNode();
                    if (anchor != null) {
                        Long pivotId = anchor.getId();
                        Long decisionId = first.getId();
                        // 두 슬롯 모두 시도(일치하는 쪽만 1행 갱신, 나머지는 0행 -> 무시)
                        baseNodeRepository.unlinkAlt1IfMatches(pivotId, decisionId);
                        baseNodeRepository.unlinkAlt2IfMatches(pivotId, decisionId);
                    }
                    // ★ 만약 앞으로 "결정 노드에서 분기 시작" 케이스가 생기면,
                    //    그 타입의 앵커(예: DecisionNode 쪽 링크 필드)를 여기서 추가로 해제하면 됨.
                });

        return new DecisionLineLifecycleDto(line.getId(), line.getStatus());
    }

    // 라인 완료
    public DecisionLineLifecycleDto completeDecisionLine(Long decisionLineId) {
        DecisionLine line = support.requireDecisionLine(decisionLineId);
        try { line.complete(); } catch (RuntimeException e) { throw support.mapDomainToApi(e); }
        decisionLineRepository.save(line);
        return new DecisionLineLifecycleDto(line.getId(), line.getStatus());
    }

    // 포크: 부모 노드까지 복제 + 부모 노드 선택지만 교체
    public DecNodeDto forkFromDecision(ForkFromDecisionRequest req) {
        if (req == null || req.parentDecisionNodeId() == null)
            throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "parentDecisionNodeId is required");
        if (req.targetOptionIndex() == null || req.targetOptionIndex() < 0 || req.targetOptionIndex() > 2)
            throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "targetOptionIndex out of range");

        DecisionNode parent = decisionNodeRepository.findById(req.parentDecisionNodeId())
                .orElseThrow(() -> new ApiException(ErrorCode.NODE_NOT_FOUND, "Parent DecisionNode not found: " + req.parentDecisionNodeId()));
        DecisionLine originLine = parent.getDecisionLine();

        DecisionLine newLine = decisionLineRepository.save(
                DecisionLine.builder()
                        .user(originLine.getUser())
                        .baseLine(originLine.getBaseLine())
                        .status(DecisionLineStatus.DRAFT)
                        .build()
        );

        List<BaseNode> orderedBase = support.getOrderedBaseNodes(originLine.getBaseLine().getId());
        List<DecisionNode> orderedOrigin = decisionNodeRepository
                .findByDecisionLine_IdOrderByAgeYearAscIdAsc(originLine.getId());

        DecisionNode prevNew = null;
        DecNodeDto forkPointDto = null;
        for (DecisionNode n : orderedOrigin) {
            boolean isBeforeParent = n.getId() < parent.getId() && n.getAgeYear() <= parent.getAgeYear();
            boolean isParent = n.getId().equals(parent.getId());

            if (!isBeforeParent && !isParent) break;

            BaseNode matchedBase = null;
            try { matchedBase = support.findBaseNodeByAge(orderedBase, n.getAgeYear()); } catch (RuntimeException ignore) {}

            List<String> options = support.extractOptions(n);
            Integer selIdx = n.getSelectedIndex();

            // 포크 지점이면 선택지만 교체
            if (isParent) {
                if (options == null || options.isEmpty())
                    throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "fork requires options at parent node");
                if (req.targetOptionIndex() >= options.size())
                    throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "targetOptionIndex >= options.size");

                selIdx = req.targetOptionIndex();
            }

            String situation = n.getSituation();
            String finalDecision = (options != null && selIdx != null && selIdx >= 0 && selIdx < options.size())
                    ? options.get(selIdx)
                    : situation;

            String background = support.resolveBackground(situation);
            NodeMappers.DecisionNodeCtxMapper mapper =
                    new NodeMappers.DecisionNodeCtxMapper(n.getUser(), newLine, prevNew, matchedBase, background);

            DecisionNodeCreateRequestDto createReq = new DecisionNodeCreateRequestDto(
                    newLine.getId(),
                    prevNew != null ? prevNew.getId() : null,
                    matchedBase != null ? matchedBase.getId() : null,
                    n.getCategory(),
                    situation,
                    finalDecision,
                    n.getAgeYear(),
                    options,
                    selIdx,
                    prevNew != null ? prevNew.getSelectedIndex() : null, // 부모 선택 인덱스 추적(선택)
                    n.getDescription()
            );

            DecisionNode saved = decisionNodeRepository.save(mapper.toEntity(createReq));
            prevNew = saved;

            if (isParent) {
                forkPointDto = mapper.toResponse(saved); // 가장 중요한: 포크 지점의 새 노드 반환
            }

            // keepUntilParent=false면 포크 노드 이전까지만 복제(그 직후 루프 탈출)
            if (Boolean.FALSE.equals(req.keepUntilParent()) && isBeforeParent) {
                // 이전까지만 복제하고 끝내려면 여기서 break; (요구에 맞게 조절)
            }
        }

        if (forkPointDto == null)
            throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "fork parent not materialized");

        return forkPointDto;
    }




}
