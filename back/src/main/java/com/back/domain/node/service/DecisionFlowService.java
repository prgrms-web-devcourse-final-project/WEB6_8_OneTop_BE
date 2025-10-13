/**
 * DecisionFlowService (개선판)
 * - 라인 생성 시 해당 BaseLine의 기본 브랜치(main)를 라인에 부착
 * - 기존 생성/next/취소/완료/포크 흐름은 유지하고, 버전 해석은 매퍼/리졸버로 위임
 *
 * [추가 요약 - 옵션 동기화(코리더 한정 횡·종 전개, 증분 Append, 숨은 노드 포함)]
 * 1) 노드가 생성되는 시점(from-base/next/fork)마다, 트리거 노드의 (baseLineId, ageYear)를 기준으로 코리더 라인 집합을 산출한다.
 *    - 코리더 라인: 해당 라인의 첫 분기 나이(첫 from-base 또는 첫 fork)가 segAge(이번 노드 나이) 이상인 라인만 포함
 *    - 이렇게 하면 같은 나이·같은 베이스라인이라도 다른 선택지로 일찍 갈라진 라인은 제외되어 덮어쓰기가 방지됨
 * 2) 코리더에 속한 모든 라인에서 동일 ageYear의 모든 결정노드(노말/프렐류드/from-base/포크 포함)를 수집한다.
 * 3) 후보 옵션들(트리거+각 노드)을 정규화(<=3, trim)한 뒤, prefix 충돌 없는 가장 긴 리스트를 리더로 선정한다.
 * 4) 리더를 증분 Append로만 반영(순서 보존, 덮어쓰기 금지), selectedIndex는 범위를 벗어나면 null 보정한다.
 */

package com.back.domain.node.service;

import com.back.domain.node.dto.decision.*;
import com.back.domain.node.entity.*;
import com.back.domain.node.mapper.NodeMappers;
import com.back.domain.node.repository.*;
import com.back.global.ai.vector.AIVectorService;
import com.back.global.exception.ApiException;
import com.back.global.exception.ErrorCode;
import com.back.global.security.CustomUserDetails;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class DecisionFlowService {

    private final DecisionLineRepository decisionLineRepository;
    private final DecisionNodeRepository decisionNodeRepository;
    private final BaseNodeRepository baseNodeRepository;
    private final BaselineBranchRepository branchRepo;
    private final NodeDomainSupport support;
    private final AIVectorService aiVectorService;
    private final BaselineCommitRepository commitRepo;

    private final NodeMappers mappers;
    @PersistenceContext
    private EntityManager entityManager;

    // from-base 생성 시 main 브랜치를 보장
    @Transactional
    public DecNodeDto createDecisionNodeFromBase(DecisionNodeFromBaseRequest request) {
        // 한줄 요약: 피벗/옵션 검증 후 라인 생성하고 main 브랜치를 반드시 부착
        if (request == null || request.baseLineId() == null)
            throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "baseLineId is required");

        Integer selRaw = request.selectedAltIndex();
        if (selRaw == null || (selRaw != 0 && selRaw != 1)) {
            throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "selectedAltIndex must be 0 or 1");
        }

        // 0) 베이스/피벗 해석
        List<BaseNode> ordered = support.getOrderedBaseNodes(request.baseLineId());
        int pivotAge = support.resolvePivotAge(request.pivotOrd(), request.pivotAge(),
                support.allowedPivotAges(ordered));
        BaseNode pivot = support.findBaseNodeByAge(ordered, pivotAge);

        // 1) 권한·입력 검증
        support.ensureOwnerOfBaseLine(request.userId(), pivot.getBaseLine());
        int sel = support.requireAltIndex(request.selectedAltIndex());

        // main 브랜치가 없으면 생성+init 커밋까지 보장
        BaselineBranch main = ensureMainBranch(pivot.getBaseLine().getId(), pivot.getUser().getId());

        // === 크리티컬 섹션 시작: 같은 pivot/slot 동시성 차단 ===
        return support.withSlotLock(pivot.getId(), sel, () -> {
            BaseNode fresh = entityManager.contains(pivot)
                    ? pivot
                    : entityManager.find(BaseNode.class, pivot.getId());
            entityManager.lock(fresh, LockModeType.PESSIMISTIC_WRITE);
            entityManager.refresh(fresh);

            Long currentTarget = (sel == 0) ? fresh.getAltOpt1TargetDecisionId() : fresh.getAltOpt2TargetDecisionId();
            if (currentTarget != null) throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "branch slot already linked");

            List<String> opts = request.options();
            Integer selectedIndex = request.selectedIndex();
            if (opts != null && !opts.isEmpty()) {
                support.validateOptionsForFromBase(opts, selectedIndex);
                support.upsertPivotAltTextsForFromBase(fresh, opts, sel);
                baseNodeRepository.save(fresh);
            }

            String chosenNow = (sel == 0) ? fresh.getAltOpt1() : fresh.getAltOpt2();
            String finalDecision = (opts != null && !opts.isEmpty() && selectedIndex != null
                    && selectedIndex >= 0 && selectedIndex < opts.size())
                    ? opts.get(selectedIndex)
                    : (opts != null && opts.size() == 1 ? opts.get(0) : chosenNow);
            if (finalDecision == null || finalDecision.isBlank())
                throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "empty branch slot and no options");

            // 라인 생성(+ main 부착 보장)
            DecisionLine line = decisionLineRepository.save(
                    DecisionLine.builder()
                            .user(fresh.getUser())
                            .baseLine(fresh.getBaseLine())
                            .baseBranch(main)
                            .status(DecisionLineStatus.DRAFT)
                            .build()
            );

            DecisionNode lineHead = createDecisionLineHead(line, ordered);
            DecisionNode preludeTail = createPreludeUntilPivot(line, ordered, pivotAge, lineHead);

            String situation = (request.situation() != null) ? request.situation() : fresh.getSituation();
            String background = support.resolveBackground(situation);

            NodeMappers.DecisionNodeCtxMapper mapper =
                    mappers.new DecisionNodeCtxMapper(fresh.getUser(), line, preludeTail, fresh, background);

            Integer normalizedSelected = (opts != null && opts.size() == 1 && selectedIndex == null) ? 0 : selectedIndex;

            DecisionNodeCreateRequestDto createReq = new DecisionNodeCreateRequestDto(
                    line.getId(), preludeTail != null ? preludeTail.getId() : null, fresh.getId(),
                    request.category() != null ? request.category() : fresh.getCategory(),
                    situation, finalDecision, pivotAge,
                    opts, normalizedSelected, sel, request.description()
            );

            DecisionNode saved = decisionNodeRepository.save(mapper.toEntity(createReq));

            int updated = (sel == 0)
                    ? baseNodeRepository.linkAlt1IfEmpty(fresh.getId(), saved.getId())
                    : baseNodeRepository.linkAlt2IfEmpty(fresh.getId(), saved.getId());
            if (updated == 0) throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "branch slot was taken by another request");

            // 숨은 노드 포함 옵션 동기화 — 증분 Append, 순서 보존
            // 트리거 옵션 결정(입력값 우선, 없으면 엔티티에서 추출)
            List<String> leaderOpts = (opts != null && !opts.isEmpty()) ? opts : support.extractOptions(saved);
            syncOptionsAcrossAgeWithinCorridorLite(saved, leaderOpts);

            DecNodeDto baseDto = mapper.toResponse(saved);
            List<DecisionNode> orderedList = decisionNodeRepository.findByDecisionLine_IdOrderByAgeYearAscIdAsc(baseDto.decisionLineId());
            var hint = aiVectorService.generateNextHint(baseDto.userId(), baseDto.decisionLineId(), orderedList);

            saved.setAiHint(hint.aiNextSituation(), hint.aiNextRecommendedOption());
            decisionNodeRepository.save(saved);

            return new DecNodeDto(
                    baseDto.id(), baseDto.userId(), baseDto.type(), baseDto.category(),
                    baseDto.situation(), baseDto.decision(), baseDto.ageYear(),
                    baseDto.decisionLineId(), baseDto.parentId(), baseDto.baseNodeId(),
                    baseDto.background(), baseDto.options(), baseDto.selectedIndex(),
                    baseDto.parentOptionIndex(), baseDto.description(),
                    hint.aiNextSituation(), hint.aiNextRecommendedOption(),
                    baseDto.followPolicy(), baseDto.pinnedCommitId(), baseDto.virtual(),
                    baseDto.effectiveCategory(), baseDto.effectiveSituation(), baseDto.effectiveDecision(),
                    baseDto.effectiveOptions(), baseDto.effectiveDescription()
            );
        });
    }

    // BaseLine에서 main 브랜치를 보장하고, 없으면 생성 + init 커밋 연결 후 반환
    private BaselineBranch ensureMainBranch(Long baseLineId, Long authorUserId) {
        return branchRepo.findByBaseLine_IdAndName(baseLineId, "main")
                .orElseGet(() -> {
                    BaselineBranch created = branchRepo.save(BaselineBranch.builder()
                            .baseLine(support.getOrderedBaseNodes(baseLineId).isEmpty()
                                    ? null
                                    : support.getOrderedBaseNodes(baseLineId).get(0).getBaseLine())
                            .name("main")
                            .headCommit(null)
                            .build());
                    BaselineCommit root = commitRepo.save(BaselineCommit.newCommit(created, null, authorUserId, "init"));
                    created.moveHeadTo(root);
                    return branchRepo.save(created);
                });
    }

    // next 서버 해석(부모 기준 라인/다음 피벗/베이스 매칭 결정)
    @Transactional
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
            support.validateOptions(request.options(), request.selectedIndex(),
                    request.selectedIndex() != null ? request.options().get(request.selectedIndex()) : null);
        }

        String situation = (request.situation() != null) ? request.situation() : parent.getSituation();
        String background = support.resolveBackground(situation);

        NodeMappers.DecisionNodeCtxMapper mapper =
                mappers.new DecisionNodeCtxMapper(parent.getUser(), line, parent, matchedBase, background);

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
                request.options(), request.selectedIndex(), request.parentOptionIndex(),
                request.description()
        );

        DecisionNode saved = decisionNodeRepository.save(mapper.toEntity(createReq));
        DecNodeDto baseDto = mapper.toResponse(saved);

        // ★ 추가: 코리더 한정 옵션 동기화(숨은 노드 포함, 증분 Append)
        List<String> leaderOpts = (request.options() != null && !request.options().isEmpty())
                ? request.options()
                : support.extractOptions(saved);
        syncOptionsAcrossAgeWithinCorridorLite(saved, leaderOpts);

        List<DecisionNode> ordered_decision = decisionNodeRepository
                .findByDecisionLine_IdOrderByAgeYearAscIdAsc(baseDto.decisionLineId());
        var hint = aiVectorService.generateNextHint(baseDto.userId(), baseDto.decisionLineId(), ordered_decision);

        saved.setAiHint(hint.aiNextSituation(), hint.aiNextRecommendedOption());
        decisionNodeRepository.save(saved);

        return new DecNodeDto(
                baseDto.id(), baseDto.userId(), baseDto.type(), baseDto.category(),
                baseDto.situation(), baseDto.decision(), baseDto.ageYear(),
                baseDto.decisionLineId(), baseDto.parentId(), baseDto.baseNodeId(),
                baseDto.background(), baseDto.options(), baseDto.selectedIndex(),
                baseDto.parentOptionIndex(), baseDto.description(),
                hint.aiNextSituation(), hint.aiNextRecommendedOption(),
                baseDto.followPolicy(), baseDto.pinnedCommitId(), baseDto.virtual(),
                baseDto.effectiveCategory(), baseDto.effectiveSituation(), baseDto.effectiveDecision(),
                baseDto.effectiveOptions(), baseDto.effectiveDescription()
        );
    }

    // 라인 취소 후 피벗 슬롯 언링크 + 슬롯 텍스트 비우기
    @Transactional
    public DecisionLineLifecycleDto cancelDecisionLine(Long decisionLineId) {
        // 라인을 취소하고, 피벗에서 시작된 첫 결정 노드를 기준으로 분기 슬롯 링크 해제 및 슬롯 텍스트 제거
        DecisionLine line = support.requireDecisionLine(decisionLineId);

        Long currentUserId = currentUserId();
        if (!line.getUser().getId().equals(currentUserId)) {
            throw new ApiException(ErrorCode.HANDLE_ACCESS_DENIED, "not line owner");
        }

        try {
            line.cancel();
        } catch (RuntimeException e) {
            throw support.mapDomainToApi(e);
        }
        decisionLineRepository.save(line);

        // 시간순으로 읽어와 '피벗 기원' 첫 노드 찾기
        decisionNodeRepository.findByDecisionLine_IdOrderByAgeYearAscIdAsc(line.getId())
                .stream()
                .filter(d -> d.getParentOptionIndex() != null) // 피벗에서 생성된 첫 노드
                .findFirst()
                .ifPresent(pivotDec -> {
                    BaseNode pivotBase = pivotDec.getBaseNode();
                    if (pivotBase == null) return;

                    Long pivotId = pivotBase.getId();
                    Long decisionId = pivotDec.getId();
                    Integer slot = pivotDec.getParentOptionIndex();

                    // 선택 슬롯(0/1)에 맞춰 정확히 언링크
                    if (slot != null && slot == 0) {
                        baseNodeRepository.unlinkAlt1IfMatches(pivotId, decisionId);
                    } else if (slot != null && slot == 1) {
                        baseNodeRepository.unlinkAlt2IfMatches(pivotId, decisionId);
                    }

                    // 슬롯 텍스트도 비우기(언링크 이후 최신값 기준으로 안전하게 처리)
                    BaseNode fresh = support.requireBaseNodeWithId(pivotId); // 최신 상태 재조회
                    if (slot != null && slot == 0) {
                        // altOpt1TargetDecisionId가 비어있다면 텍스트 정리
                        if (fresh.getAltOpt1TargetDecisionId() == null) {
                            fresh.setAltOpt1(null);
                            baseNodeRepository.save(fresh);
                        }
                    } else if (slot != null && slot == 1) {
                        if (fresh.getAltOpt2TargetDecisionId() == null) {
                            fresh.setAltOpt2(null);
                            baseNodeRepository.save(fresh);
                        }
                    }
                });

        return new DecisionLineLifecycleDto(line.getId(), line.getStatus());
    }

    // --- 헬퍼: 현재 인증 사용자 id ---
    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails cud)) {
            // 테스트에선 .with(user(...)) 미설정 시 401
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "unauthenticated");
        }
        return cud.getUser().getId();
    }

    // 라인 완료
    @Transactional
    public DecisionLineLifecycleDto completeDecisionLine(Long decisionLineId) {
        DecisionLine line = support.requireDecisionLine(decisionLineId);
        try { line.complete(); } catch (RuntimeException e) { throw support.mapDomainToApi(e); }
        return new DecisionLineLifecycleDto(line.getId(), line.getStatus());
    }

    // 기존 라인을 부모로 하여 특정 결정노드에서 포크 라인을 만들고 AI 힌트를 엔티티에도 저장
    @Transactional
    public DecNodeDto forkFromDecision(ForkFromDecisionRequest req) {
        if (req == null || req.parentDecisionNodeId() == null)
            throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "parentDecisionNodeId is required");
        if (req.targetOptionIndex() == null || req.targetOptionIndex() < 0 || req.targetOptionIndex() > 2)
            throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "targetOptionIndex out of range");

        // 부모 노드 로드
        DecisionNode parent = decisionNodeRepository.findById(req.parentDecisionNodeId())
                .orElseThrow(() -> new ApiException(ErrorCode.NODE_NOT_FOUND,
                        "Parent DecisionNode not found: " + req.parentDecisionNodeId()));
        DecisionLine originLine = parent.getDecisionLine();

        // 새 라인에 원본 라인 id 저장(나머지 흐름 동일)
        DecisionLine newLine = decisionLineRepository.save(
                DecisionLine.builder()
                        .user(originLine.getUser())
                        .baseLine(originLine.getBaseLine())
                        .baseBranch(originLine.getBaseBranch())
                        .status(DecisionLineStatus.DRAFT)
                        .parentLineId(originLine.getId())
                        .build()
        );

        List<BaseNode> orderedBase = support.getOrderedBaseNodes(originLine.getBaseLine().getId());

        // 베이스 헤더(BaseNode.parent==null) 선별
        BaseNode baseHeader = null;
        for (BaseNode b : orderedBase) {
            if (b.getParent() == null) { baseHeader = b; break; }
        }
        if (baseHeader == null && !orderedBase.isEmpty()) baseHeader = orderedBase.get(0);

        DecisionNode prevNew = null;

        List<DecisionNode> orderedOrigin = decisionNodeRepository
                // 기존 라인의 노드 목록을 타임라인 정렬로 로드
                .findByDecisionLine_IdOrderByAgeYearAscIdAsc(originLine.getId());

        DecNodeDto forkPointDto = null;
        // 포크 앵커 엔티티를 추적해 AI 힌트 저장에 사용
        DecisionNode forkAnchorSaved = null;

        for (DecisionNode n : orderedOrigin) {
            boolean isBeforeParent = n.getAgeYear() < parent.getAgeYear();
            boolean isParent = n.getId().equals(parent.getId());
            if (!isBeforeParent && !isParent) break;

            BaseNode matchedBase = null;
            try {
                // 헤더면 베이스 헤더로, 아니면 age로 매칭
                if (n.getParent() == null) {
                    matchedBase = baseHeader; // 헤더는 항상 베이스 헤더로 고정
                } else {
                    matchedBase = support.findBaseNodeByAge(orderedBase, n.getAgeYear());
                }
            } catch (RuntimeException ignore) {}

            // 기본 옵션/선택은 원본 라인의 것을 사용
            List<String> options = support.extractOptions(n);
            Integer selIdx = n.getSelectedIndex();

            if (isParent) {
                // 포크 앵커에서 선택지 교체/강제 규칙
                if (req.options() != null && !req.options().isEmpty()) {
                    // 옵션 검증(1~3개, selectedIndex 일치)
                    support.validateOptions(
                            req.options(),
                            req.selectedIndex(),
                            (req.selectedIndex() != null && req.selectedIndex() >= 0
                                    && req.selectedIndex() < req.options().size())
                                    ? req.options().get(req.selectedIndex())
                                    : null
                    );
                    options = req.options();
                    selIdx = (req.selectedIndex() == null && options.size() == 1) ? 0 : req.selectedIndex();
                } else {
                    // 입력 옵션이 없으면 기존 옵션 사용 + targetOptionIndex 강제
                    if (options == null || options.isEmpty())
                        throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "fork requires options at parent node");
                    if (req.targetOptionIndex() >= options.size())
                        throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "targetOptionIndex >= options.size");
                    selIdx = req.targetOptionIndex();
                }
            }

            String situation = n.getSituation();
            String finalDecision =
                    (options != null && selIdx != null && selIdx >= 0 && selIdx < options.size())
                            ? options.get(selIdx)
                            : situation;

            String background = support.resolveBackground(situation);
            NodeMappers.DecisionNodeCtxMapper mapper =
                    mappers.new DecisionNodeCtxMapper(n.getUser(), newLine, prevNew, matchedBase, background);

            Long newParentId = (n.getParent() == null) ? null : (prevNew != null ? prevNew.getId() : null);

            // 포크 앵커에서 parentOptionIndex 강제 -> 라벨러가 'fork'로 인식
            Integer parentOptionIndexForCreate = isParent ? req.targetOptionIndex() : null;

            DecisionNodeCreateRequestDto createReq = new DecisionNodeCreateRequestDto(
                    newLine.getId(),
                    newParentId,
                    matchedBase != null ? matchedBase.getId() : null,
                    n.getCategory(),
                    situation,
                    finalDecision,
                    n.getAgeYear(),
                    options,
                    selIdx,
                    parentOptionIndexForCreate,
                    n.getDescription()
            );

            DecisionNode saved = decisionNodeRepository.save(mapper.toEntity(createReq));
            prevNew = saved;

            // 포크 진행 중에도 해당 age에서 코리더 한정 동기화(프렐류드 포함)
            List<String> leaderOptsHere = (options != null && !options.isEmpty())
                    ? options
                    : support.extractOptions(saved);
            syncOptionsAcrossAgeWithinCorridorLite(saved, leaderOptsHere);

            if (isParent) {
                forkPointDto = mapper.toResponse(saved);
                forkAnchorSaved = saved; // AI 힌트 저장 대상으로 보관
            }
        }

        if (forkPointDto == null)
            throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "fork parent not materialized");

        // 새 라인의 노드를 타임라인 정렬로 재조회하여 AI 힌트 생성
        List<DecisionNode> orderedNew =
                decisionNodeRepository.findByDecisionLine_IdOrderByAgeYearAscIdAsc(forkPointDto.decisionLineId());
        var hint = aiVectorService.generateNextHint(forkPointDto.userId(), forkPointDto.decisionLineId(), orderedNew);

        // 생성된 AI 힌트를 앵커 엔티티에 저장(영속)
        if (forkAnchorSaved != null) {
            forkAnchorSaved.setAiHint(hint.aiNextSituation(), hint.aiNextRecommendedOption());
            decisionNodeRepository.save(forkAnchorSaved);
        }

        return new DecNodeDto(
                forkPointDto.id(), forkPointDto.userId(), forkPointDto.type(), forkPointDto.category(),
                forkPointDto.situation(), forkPointDto.decision(), forkPointDto.ageYear(),
                forkPointDto.decisionLineId(), forkPointDto.parentId(), forkPointDto.baseNodeId(),
                forkPointDto.background(), forkPointDto.options(), forkPointDto.selectedIndex(),
                forkPointDto.parentOptionIndex(), forkPointDto.description(),
                hint.aiNextSituation(), hint.aiNextRecommendedOption(),
                forkPointDto.followPolicy(), forkPointDto.pinnedCommitId(), forkPointDto.virtual(),
                forkPointDto.effectiveCategory(), forkPointDto.effectiveSituation(),
                forkPointDto.effectiveDecision(), forkPointDto.effectiveOptions(),
                forkPointDto.effectiveDescription()
        );
    }


    // 베이스 헤더를 찾아 결정 라인 헤더 생성
    private DecisionNode createDecisionLineHead(DecisionLine line, List<BaseNode> orderedBase) {
        if (orderedBase == null || orderedBase.isEmpty()) return null;

        BaseNode baseHeader = null;
        for (BaseNode n : orderedBase) {
            if (n.getParent() == null) { baseHeader = n; break; }
        }
        if (baseHeader == null) baseHeader = orderedBase.get(0);

        String situation = baseHeader.getSituation();
        String decision = baseHeader.getDecision();
        String background = support.resolveBackground(situation);

        NodeMappers.DecisionNodeCtxMapper mapper =
                mappers.new DecisionNodeCtxMapper(line.getUser(), line, null, baseHeader, background);

        DecisionNodeCreateRequestDto headReq = new DecisionNodeCreateRequestDto(
                line.getId(),
                null,
                baseHeader.getId(),
                baseHeader.getCategory(),
                situation,
                (decision != null && !decision.isBlank()) ? decision : situation,
                baseHeader.getAgeYear(),
                null, null, null,
                null
        );
        return decisionNodeRepository.save(mapper.toEntity(headReq));
    }

    // 헤더 다음에 피벗 직전까지 복제
    private DecisionNode createPreludeUntilPivot(DecisionLine line,
                                                 List<BaseNode> orderedBase,
                                                 int pivotAge,
                                                 DecisionNode lineHead) {
        DecisionNode prev = lineHead;
        for (BaseNode b : orderedBase) {
            if (b.getParent() == null) continue;
            if (b.getAgeYear() >= pivotAge) break;

            String situation = b.getSituation();
            String decision  = (b.getDecision() != null && !b.getDecision().isBlank()) ? b.getDecision() : situation;
            String background = support.resolveBackground(situation);

            NodeMappers.DecisionNodeCtxMapper mapper =
                    mappers.new DecisionNodeCtxMapper(line.getUser(), line, prev, b, background);

            DecisionNodeCreateRequestDto req = new DecisionNodeCreateRequestDto(
                    line.getId(),
                    prev != null ? prev.getId() : null,
                    b.getId(),
                    b.getCategory(),
                    situation,
                    decision,
                    b.getAgeYear(),
                    null, null,
                    prev != null ? prev.getSelectedIndex() : null,
                    null
            );
            prev = decisionNodeRepository.save(mapper.toEntity(req));
        }
        return prev;
    }


    // 코리더 집합(첫 분기 나이 ≥ segAge) 안의 동일 age 노드를 증분 동기화
    @Transactional
    protected void syncOptionsAcrossAgeWithinCorridorLite(DecisionNode trigger, List<String> triggerOptions) {
        if (trigger == null || trigger.getDecisionLine() == null) return;

        final Integer segAge = trigger.getAgeYear();
        final DecisionLine triggerLine = trigger.getDecisionLine();
        if (segAge == null || triggerLine.getBaseLine() == null) return;
        final Long baseLineId = triggerLine.getBaseLine().getId();

        // 베이스라인의 모든 라인 조회
        List<DecisionLine> allLines = decisionLineRepository.findByBaseLine_Id(baseLineId);
        if (allLines == null || allLines.isEmpty()) return;

        // 라인별 첫 from-base 나이/첫 fork 나이 계산
        Map<Long, LineBoundary> boundaryByLine = new HashMap<>();
        for (DecisionLine ln : allLines) {
            Integer fromBaseAge = null, forkAge = null;
            List<DecisionNode> seq = decisionNodeRepository.findByDecisionLine_IdOrderByAgeYearAscIdAsc(ln.getId());
            for (DecisionNode n : seq) {
                if (fromBaseAge == null && isFromBaseMark(n)) fromBaseAge = n.getAgeYear();
                if (forkAge == null && n.getParentOptionIndex() != null) forkAge = n.getAgeYear();
                if (fromBaseAge != null && forkAge != null) break;
            }
            boundaryByLine.put(ln.getId(), new LineBoundary(fromBaseAge, forkAge));
        }

        // 첫 분기 경계 나이가 segAge 이상인 라인만 코리더로 선별
        Set<Long> corridorLineIds = new HashSet<>();
        for (DecisionLine ln : allLines) {
            if (ln.getStatus() == DecisionLineStatus.CANCELLED) continue;
            LineBoundary lb = boundaryByLine.get(ln.getId());
            int boundary = (lb != null) ? lb.firstDivergenceAge() : Integer.MAX_VALUE;
            if (segAge <= boundary) corridorLineIds.add(ln.getId());
        }
        if (corridorLineIds.isEmpty()) return;

        // 동일 ageYear 대상 노드 수집(노말/프렐류드/from-base/포크 포함)
        List<DecisionNode> group = new ArrayList<>();
        for (DecisionLine ln : allLines) {
            if (!corridorLineIds.contains(ln.getId())) continue;
            List<DecisionNode> seq = decisionNodeRepository.findByDecisionLine_IdOrderByAgeYearAscIdAsc(ln.getId());
            for (DecisionNode n : seq) if (segAge.equals(n.getAgeYear())) group.add(n);
        }
        if (group.isEmpty()) return;

        // 리더 옵션 선정(정규화 + prefix 충돌 없는 최장 리스트)
        Function<List<String>, List<String>> normalize = opts -> {
            if (opts == null) return List.of();
            return opts.stream().filter(s -> s != null && !s.isBlank())
                    .map(String::trim).limit(3).toList();
        };
        List<List<String>> candidates = new ArrayList<>();
        candidates.add(normalize.apply(triggerOptions != null ? triggerOptions : support.extractOptions(trigger)));
        for (DecisionNode n : group) candidates.add(normalize.apply(support.extractOptions(n)));

        BiFunction<List<String>, List<String>, Boolean> prefixOk = (a, b) -> {
            int m = Math.min(a.size(), b.size());
            for (int i = 0; i < m; i++) if (!Objects.equals(a.get(i), b.get(i))) return false;
            return true;
        };
        List<String> leader = List.of();
        outer:
        for (List<String> cand : candidates) {
            if (cand == null) continue;
            for (List<String> other : candidates) {
                if (other == null) continue;
                if (!(prefixOk.apply(cand, other) || prefixOk.apply(other, cand))) continue outer;
            }
            if (cand.size() > leader.size()) leader = cand;
        }
        if (leader.isEmpty()) return;

        // 증분 Append 반영(순서 보존, 숨은 노드 포함)
        for (DecisionNode node : group) {
            List<String> cur = normalize.apply(support.extractOptions(node));
            boolean ok = true;
            for (int i = 0; i < Math.min(cur.size(), leader.size()); i++) {
                if (!Objects.equals(cur.get(i), leader.get(i))) { ok = false; break; }
            }
            if (!ok) continue;

            String o1 = null, o2 = null, o3 = null;
            int sz = Math.min(3, leader.size());
            if (sz >= 1) o1 = leader.get(0);
            if (sz >= 2) o2 = leader.get(1);
            if (sz >= 3) o3 = leader.get(2);

            node.setOption1(o1);
            node.setOption2(o2);
            node.setOption3(o3);

            Integer sel = node.getSelectedIndex();
            int eff = (o3 != null) ? 3 : (o2 != null ? 2 : (o1 != null ? 1 : 0));
            if (sel != null && (sel < 0 || sel >= eff)) node.setSelectedIndex(null);

            decisionNodeRepository.save(node);
        }
    }

    // from-base 표식 여부(피벗 슬롯 타깃 매칭으로 판정)
    private boolean isFromBaseMark(DecisionNode n) {
        BaseNode b = n.getBaseNode();
        if (b == null) return false;
        Long id = n.getId();
        return Objects.equals(b.getAltOpt1TargetDecisionId(), id)
                || Objects.equals(b.getAltOpt2TargetDecisionId(), id);
    }

    // 라인의 첫 분기 경계 나이 스냅샷 컨테이너
    private static final class LineBoundary {
        final Integer fromBaseAge; // null 허용
        final Integer forkAge;     // null 허용
        LineBoundary(Integer fromBaseAge, Integer forkAge) {
            this.fromBaseAge = fromBaseAge;
            this.forkAge = forkAge;
        }
        // 첫 분기 경계 나이(없으면 무한대)
        int firstDivergenceAge() {
            Integer a = (forkAge != null) ? forkAge : fromBaseAge;
            return (a != null) ? a : Integer.MAX_VALUE;
        }
    }
}
