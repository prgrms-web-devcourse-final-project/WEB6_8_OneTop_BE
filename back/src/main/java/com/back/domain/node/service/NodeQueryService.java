/**
 * NodeQueryService (개선판)
 * - 읽기 경로를 인스턴스 매퍼로 통일하여 버전 해석 값(effective*)을 포함해 반환
 * - 기존 응답 계약은 유지
 */
package com.back.domain.node.service;

import com.back.domain.node.dto.TreeDto;
import com.back.domain.node.dto.base.BaseLineDto;
import com.back.domain.node.dto.base.BaseNodeDto;
import com.back.domain.node.dto.decision.DecNodeDto;
import com.back.domain.node.dto.decision.DecisionLineDetailDto;
import com.back.domain.node.dto.decision.DecisionLineListDto;
import com.back.domain.node.entity.*;
import com.back.domain.node.mapper.NodeMappers;
import com.back.domain.node.repository.*;
import com.back.domain.user.entity.User;
import com.back.domain.user.repository.UserRepository;
import com.back.global.exception.ApiException;
import com.back.global.exception.ErrorCode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NodeQueryService {

    private final UserRepository userRepository;
    private final BaseNodeRepository baseNodeRepository;
    private final DecisionNodeRepository decisionNodeRepository;
    private final DecisionLineRepository decisionLineRepository;
    private final BaseLineRepository baseLineRepository;

    private final VersionResolver versionResolver;
    private final NodeAtomVersionRepository versionRepo;

    private final NodeMappers mappers;
    private final NodeDomainSupport support;

    private final ObjectMapper objectMapper;

    /*
     * [TreeQuery] BaseLine 트리 조회 (하드닝 적용판)
     * - 목적: 포크 앵커의 pivotLinkDecisionNodeId가 반드시 (background|ageYear|parentLineId)로만 매칭되도록 강제
     * - 흐름 개요
     *   1) 베이스/라인/노드 조회 및 DTO 매핑 → 보조 인덱스(pivot/children) 구축
     *   2) 라인별 메타 계산: indexInLine, 첫 from-base, from-base 라인 여부, 첫 fork 앵커
     *   3) 원본 normal 인덱스 구축: key = (background|ageYear|lineId) + 중복(normal) 검증
     *   4) 포크 라인의 parentLineId 일관성 검증(from-base 라인은 null, 포크 라인은 not null)
     *   5) 각 노드에 edge 라벨(root/from-base/prelude/fork/normal)·pivotLink 채움
     *      - 포크 앵커의 결정 피벗 링크는 (background|ageYear|parentLineId)로 '정확 매칭', 미스면 즉시 예외
     *   6) renderPhase(라인 위상) → ageYear → id 기준 정렬 후 TreeDto 반환
     */

    // 가장 중요한 함수 한줄 요약: BaseLine 전체 트리를 라벨링/피벗링크/검증 강화하여 TreeDto로 반환
    public TreeDto getTreeForBaseLine(Long baseLineId) {
        // 가장 많이 사용하는 호출 한줄 요약: 베이스라인 존재 보장
        support.ensureBaseLineExists(baseLineId);

        // 가장 많이 사용하는 호출 한줄 요약: BaseNode 정렬 조회 및 DTO 매핑
        List<BaseNode> orderedBase = support.getOrderedBaseNodes(baseLineId);
        List<BaseNodeDto> baseDtos = orderedBase.stream()
                // 가장 많이 사용하는 호출 한줄 요약: BaseNode → BaseNodeDto 매핑
                .map(mappers.BASE_READ::map)
                .toList();

        Map<Long, PivotMark> pivotIndex = buildPivotIndex(baseLineId);

        record View(
                DecisionNode dn,
                DecNodeDto dto,
                boolean isRoot,
                Long baseId,
                Integer age,
                List<Long> childrenIds,
                Integer pivotSlot
        ) {}

        // 가장 많이 사용하는 호출 한줄 요약: 베이스라인의 모든 라인 조회
        List<DecisionLine> lines = decisionLineRepository.findByBaseLine_Id(baseLineId);

        // ★ 라인 메타(parentLineId) 사전 구축
        Map<Long, Long> parentLineIdByLine = new HashMap<>();
        for (DecisionLine ln : lines) parentLineIdByLine.put(ln.getId(), ln.getParentLineId());

        List<View> pool = new ArrayList<>();
        Map<Long, List<View>> byLine = new HashMap<>();

        for (DecisionLine line : lines) {
            List<DecisionNode> ordered = decisionNodeRepository
                    // 가장 많이 사용하는 호출 한줄 요약: 라인의 노드를 타임라인 정렬로 조회
                    .findByDecisionLine_IdOrderByAgeYearAscIdAsc(line.getId());
            Map<Long, List<Long>> childrenIndex = buildChildrenIndex(ordered);

            for (DecisionNode dn : ordered) {
                // 가장 많이 사용하는 호출 한줄 요약: DecisionNode → DecNodeDto 매핑
                DecNodeDto dto = mappers.DECISION_READ.map(dn);

                boolean isRoot = (dn.getParent() == null);
                List<Long> childrenIds = childrenIndex.getOrDefault(dn.getId(), List.of());

                PivotMark mark = pivotIndex.get(dto.id());
                Long pivotBaseId = (mark != null) ? mark.baseNodeId()
                        : (dn.getBaseNode() != null ? dn.getBaseNode().getId() : null);
                Integer pivotSlot = (mark != null) ? mark.slotIndex() : null;

                View v = new View(
                        dn, dto, isRoot,
                        pivotBaseId, dn.getAgeYear(),
                        List.copyOf(childrenIds), pivotSlot
                );
                pool.add(v);
                byLine.computeIfAbsent(line.getId(), k -> new ArrayList<>()).add(v);
            }
        }

        // 1) 라인별 보조 인덱스: 노드 순서, 첫 from-base, from-base 라인 여부, 첫 fork 앵커
        Map<Long, Map<Long, Integer>> indexInLine = new HashMap<>();
        Map<Long, Long> firstFromBaseNodeIdByLine = new HashMap<>();
        Map<Long, Boolean> isFromBaseLine = new HashMap<>();
        Map<Long, Long> firstForkNodeIdByLine = new HashMap<>();

        for (Map.Entry<Long, List<View>> e : byLine.entrySet()) {
            Long lineId = e.getKey();
            List<View> vs = e.getValue().stream()
                    .sorted(Comparator.comparing((View x) -> x.age).thenComparing(x -> x.dn.getId()))
                    .toList();

            Map<Long, Integer> idx = new HashMap<>();
            for (int i = 0; i < vs.size(); i++) idx.put(vs.get(i).dn.getId(), i);
            indexInLine.put(lineId, idx);

            // from-base 라인: pivotSlot 있는 첫 노드(여러 번 from-base 가능해도 라인 판정은 첫 발생으로 충분)
            Long firstFromBase = null;
            for (View v : vs) {
                if (v.pivotSlot != null) { firstFromBase = v.dn.getId(); break; }
            }
            if (firstFromBase != null) {
                firstFromBaseNodeIdByLine.put(lineId, firstFromBase);
                isFromBaseLine.put(lineId, true);
            } else {
                isFromBaseLine.put(lineId, false);
            }

            // fork 라인의 "첫 fork 노드": parentOptionIndex != null 인 첫 노드 (from-base 라인 제외)
            if (!isFromBaseLine.get(lineId)) {
                for (View v : vs) {
                    if (v.dto.parentOptionIndex() != null) {
                        firstForkNodeIdByLine.put(lineId, v.dn.getId());
                        break;
                    }
                }
            }
        }

        // 1-보강) parentLineId 일관성 검증: from-base 라인은 null, 포크 라인은 not null(앵커 존재 시)
        for (Long lineId : byLine.keySet()) {
            boolean hasForkAnchor = firstForkNodeIdByLine.containsKey(lineId);
            Long pli = parentLineIdByLine.get(lineId);

            if (Boolean.TRUE.equals(isFromBaseLine.getOrDefault(lineId, false))) {
                if (pli != null)
                    throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "from-base line must have null parentLineId: line=" + lineId);
            } else {
                if (hasForkAnchor && pli == null)
                    throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "fork line without parentLineId: line=" + lineId);
            }
        }

        // 2) 원본 normal 노드 인덱스(A안 핵심): key = background + "|" + ageYear + "|" + lineId
        // parentOptionIndex == null 인 노드(= normal)만 수집, 동일 키는 가장 오래된(작은 id) 고정
        Map<String, Long> sourceNormalByKey = new HashMap<>();
        Map<String, Integer> sourceNormalCount = new HashMap<>(); // 중복(normal) 감지
        for (View v : pool) {
            DecNodeDto d = v.dto;
            String bg = d.background();
            Integer age = d.ageYear();
            Long ln = v.dn.getDecisionLine().getId();
            if (bg != null && age != null && d.parentOptionIndex() == null) {
                String k = bg + "|" + age + "|" + ln; // ★ lineId 포함(라인 분리 키)
                sourceNormalByKey.merge(k, v.dn.getId(), Math::min);
                sourceNormalCount.merge(k, 1, Integer::sum);
            }
        }
        // 2-보강) 같은 라인에서 동일 (bg,age) normal이 2개 이상이면 모호성 → 예외
        for (Map.Entry<String, Integer> e : sourceNormalCount.entrySet()) {
            if (e.getValue() > 1)
                throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "ambiguous normals for key=" + e.getKey());
        }

        // 3) renderPhase (정렬용 최소치): from-base 라인=1, 그 외(일반/포크)=2
        Map<Long, Integer> linePhase = new HashMap<>();
        for (Long lineId : byLine.keySet()) {
            linePhase.put(lineId, isFromBaseLine.getOrDefault(lineId, false) ? 1 : 2);
        }

        // 4) DTO 생성 (edge 라벨 + pivotLink 채우기)
        List<DecNodeDto> decDtos = new ArrayList<>();
        for (View v : pool) {
            DecNodeDto b = v.dto;

            Long lineId = v.dn.getDecisionLine().getId();
            Integer renderPhase = linePhase.getOrDefault(lineId, 1);

            Long incomingFromId;
            Long incomingFromLineId = null;
            if (v.isRoot) {
                incomingFromId = null; // 루트는 외부에서 들어온 에지가 없다(표시용 null)
            } else {
                DecisionNode p = v.dn.getParent();
                incomingFromId = (p != null ? p.getId() : null);
                incomingFromLineId = (p != null ? p.getDecisionLine().getId() : null);
            }

            // === edge type 규칙 ===
            String incomingEdgeType;
            if (v.isRoot) {
                incomingEdgeType = "root";
            } else if (v.pivotSlot != null) {
                // from-base 표시가 있는 노드는 무조건 from-base (여러 번 존재해도 동일 규칙)
                incomingEdgeType = "from-base";
            } else {
                Long firstForkId = firstForkNodeIdByLine.get(lineId);
                Map<Long, Integer> idxMap = indexInLine.getOrDefault(lineId, Map.of());
                Integer curIdx  = idxMap.getOrDefault(v.dn.getId(), Integer.MAX_VALUE);
                Integer forkIdx = (firstForkId != null) ? idxMap.getOrDefault(firstForkId, Integer.MAX_VALUE) : null;

                boolean sameLineFromParent = Objects.equals(incomingFromLineId, lineId);
                boolean isForkAnchor = (firstForkId != null) && Objects.equals(firstForkId, v.dn.getId());
                boolean beforeFirstFork = (firstForkId != null) && sameLineFromParent && (curIdx < forkIdx);

                if (!Boolean.TRUE.equals(isFromBaseLine.getOrDefault(lineId, false))) {
                    // 포크 라인 규칙: 앵커=fork, 앵커 이전은 prelude, 나머지 normal
                    if (isForkAnchor) {
                        incomingEdgeType = "fork";
                    } else if (beforeFirstFork) {
                        incomingEdgeType = "prelude";
                    } else {
                        incomingEdgeType = "normal";
                    }
                } else {
                    // from-base 라인 규칙: '첫 from-base' 이전만 prelude, 이후는 normal
                    Long firstFromBaseId = firstFromBaseNodeIdByLine.get(lineId);
                    Integer firstIdx = idxMap.getOrDefault(firstFromBaseId, Integer.MAX_VALUE);
                    boolean isPreludeOnFromBase = (firstFromBaseId != null)
                            && sameLineFromParent
                            && (curIdx < firstIdx);
                    incomingEdgeType = isPreludeOnFromBase ? "prelude" : "normal";
                }
            }

            // === pivotLink 채우기 ===
            // 1) 베이스 피벗 링크: from-base 표시가 있는 노드에만 설정
            Long pivotLinkBaseNodeId = (v.pivotSlot != null) ? v.baseId : null;
            Integer pivotSlot = v.pivotSlot;

            // 2) 결정 피벗 링크(하드닝 핵심):
            //    포크 앵커에 한해, 반드시 (background|ageYear|parentLineId)로 '정확 매칭'
            //    - parentLineId가 없거나, 매칭되는 normal이 없으면 즉시 예외(폴백 없음)
            Long pivotLinkDecisionNodeId = null;
            {
                Long firstForkId = firstForkNodeIdByLine.get(lineId);
                boolean isForkAnchor = (firstForkId != null) && Objects.equals(firstForkId, v.dn.getId());
                if (isForkAnchor) {
                    Long originLineId = parentLineIdByLine.get(lineId);
                    if (originLineId == null) {
                        throw new ApiException(ErrorCode.INVALID_INPUT_VALUE,
                                "fork anchor without parentLineId: line=" + lineId);
                    }
                    // 가장 많이 사용하는 호출 한줄 요약: 원본 라인의 normal을 (bg|age|originLineId)로 정확 조회
                    String kOrigin = b.background() + "|" + b.ageYear() + "|" + originLineId;
                    pivotLinkDecisionNodeId = sourceNormalByKey.get(kOrigin);
                    if (pivotLinkDecisionNodeId == null) {
                        throw new ApiException(ErrorCode.INVALID_INPUT_VALUE,
                                "origin normal not found for fork anchor: key=" + kOrigin);
                    }
                }
            }

            decDtos.add(new DecNodeDto(
                    b.id(), b.userId(), b.type(), b.category(),
                    b.situation(), b.decision(), b.ageYear(),
                    b.decisionLineId(), b.parentId(), b.baseNodeId(),
                    b.background(), b.options(), b.selectedIndex(),
                    b.parentOptionIndex(), b.description(),
                    b.aiNextSituation(), b.aiNextRecommendedOption(),
                    b.followPolicy(), b.pinnedCommitId(), b.virtual(),
                    b.effectiveCategory(), b.effectiveSituation(), b.effectiveDecision(),
                    b.effectiveOptions(), b.effectiveDescription(),
                    v.childrenIds, v.isRoot, pivotLinkBaseNodeId, pivotSlot,
                    pivotLinkDecisionNodeId,
                    renderPhase, incomingFromId, incomingEdgeType,
                    incomingFromLineId
            ));
        }

        decDtos.sort(Comparator
                .comparing(DecNodeDto::renderPhase, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(DecNodeDto::ageYear, Comparator.nullsFirst(Integer::compareTo))
                .thenComparing(DecNodeDto::id));

        return new TreeDto(baseDtos, decDtos);
    }



    // 라인별 베이스 노드 정렬 반환
    public List<BaseNodeDto> getBaseLineNodes(Long baseLineId) {
        support.ensureBaseLineExists(baseLineId);
        List<BaseNode> nodes = baseNodeRepository.findByBaseLine_IdOrderByAgeYearAscIdAsc(baseLineId);
        if (nodes == null || nodes.isEmpty()) return List.of();
        List<BaseNodeDto> result = new ArrayList<>(nodes.size());
        for (BaseNode n : nodes) result.add(mappers.BASE_READ.map(n));
        return result;
    }

    // BaseNode 단건 조회
    public BaseNodeDto getBaseNode(Long baseNodeId) {
        BaseNode node = baseNodeRepository.findById(baseNodeId)
                .orElseThrow(() -> new ApiException(ErrorCode.NODE_NOT_FOUND, "BaseNode not found: " + baseNodeId));
        return mappers.BASE_READ.map(node);
    }

    // 사용자별 결정 라인 요약 조회
    public DecisionLineListDto getDecisionLines(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND, "User not found: " + userId));

        List<DecisionLine> lines = decisionLineRepository.findByUser(user);
        List<DecisionLineListDto.LineSummary> summaries = new ArrayList<>(lines.size());

        for (DecisionLine line : lines) {
            List<DecisionNode> nodes = line.getDecisionNodes();
            nodes.sort(Comparator.comparingInt(DecisionNode::getAgeYear).thenComparing(DecisionNode::getId));

            Integer nodeCount = nodes.size();
            Integer firstAge = nodeCount > 0 ? nodes.get(0).getAgeYear() : null;
            Integer lastAge  = nodeCount > 0 ? nodes.get(nodeCount - 1).getAgeYear() : null;

            summaries.add(new DecisionLineListDto.LineSummary(
                    line.getId(),
                    line.getBaseLine().getId(),
                    line.getStatus(),
                    nodeCount,
                    firstAge,
                    lastAge,
                    line.getCreatedDate()
            ));
        }
        return new DecisionLineListDto(summaries);
    }

    // 가장 중요한: 특정 라인의 상세를 childrenIds/root/pivotLink*와 함께 반환
    public DecisionLineDetailDto getDecisionLineDetail(Long decisionLineId) {
        DecisionLine line = decisionLineRepository.findById(decisionLineId)
                .orElseThrow(() -> new ApiException(ErrorCode.DECISION_LINE_NOT_FOUND,
                        "DecisionLine not found: " + decisionLineId));

        Long baseLineId   = line.getBaseLine().getId();
        Long baseBranchId = (line.getBaseBranch() != null) ? line.getBaseBranch().getId() : null;

        // 가장 많이 사용하는: 라인 노드를 타임라인 정렬 조회
        List<DecisionNode> ordered = decisionNodeRepository
                .findByDecisionLine_IdOrderByAgeYearAscIdAsc(line.getId());

        // parent→children 인덱스 구성
        Map<Long, List<Long>> childrenIndex = buildChildrenIndex(ordered);
        // 베이스 분기 슬롯 역인덱스 구성
        Map<Long, PivotMark> pivotIndex = buildPivotIndex(baseLineId);

        List<DecNodeDto> nodes = ordered.stream().map(n -> {
            // 기존 읽기 매퍼로 기본 DTO 생성(effective* 일부 포함)
            DecNodeDto base = mappers.DECISION_READ.map(n);

            // 정책/핀/오버라이드에서 최종 버전 해석 (필요 시 덮어쓰기)
            FollowPolicy policy = n.getFollowPolicy();
            Long pinnedCommitId = base.pinnedCommitId();
            Long overrideVersionId = (n.getOverrideVersion() != null) ? n.getOverrideVersion().getId() : null;

            Long verId = versionResolver.resolveVersionId(
                    baseLineId, baseBranchId, pinnedCommitId, policy, n.getAgeYear(), overrideVersionId
            );

            NodeCategory effCategory   = base.category();
            String       effSituation  = base.situation();
            String       effDecision   = base.decision();
            List<String> effOpts       = base.options();
            String       effDesc       = base.description();

            if (verId != null) {
                NodeAtomVersion v = versionRepo.findById(verId).orElse(null);
                if (v != null) {
                    if (v.getCategory()    != null) effCategory  = v.getCategory();
                    if (v.getSituation()   != null) effSituation = v.getSituation();
                    if (v.getDecision()    != null) effDecision  = v.getDecision();
                    List<String> parsed = parseOptionsJson(v.getOptionsJson());
                    if (parsed != null)             effOpts      = parsed;
                    if (v.getDescription() != null) effDesc      = v.getDescription();
                }
            }

            List<Long> childrenIds = childrenIndex.getOrDefault(n.getId(), List.of());
            boolean isRoot = (n.getParent() == null);

            PivotMark mark = pivotIndex.get(base.id());
            Long pivotBaseId = (mark != null) ? mark.baseNodeId() : null;
            Integer pivotSlot = (mark != null) ? mark.slotIndex() : null;

            // ===== 라인 상세 전용 렌더 힌트 =====
            // 한줄 요약: 상세 화면은 한 라인만 보므로 phase=1 고정, incoming은 parent 기준(normal)
            Integer renderPhase = 1;
            Long incomingFromId = isRoot ? null : n.getParent().getId();
            String incomingEdgeType = "normal";
            Long incomingFromLineId = isRoot ? null : n.getParent().getDecisionLine().getId();
            Long pivotLinkDecisionNodeId = null;


            return new DecNodeDto(
                    base.id(), base.userId(), base.type(), base.category(),
                    base.situation(), base.decision(), base.ageYear(),
                    base.decisionLineId(), base.parentId(), base.baseNodeId(),
                    base.background(), base.options(), base.selectedIndex(),
                    base.parentOptionIndex(), base.description(),
                    base.aiNextSituation(), base.aiNextRecommendedOption(),
                    base.followPolicy(), base.pinnedCommitId(), base.virtual(),
                    // effective* (최종 해석 반영)
                    effCategory, effSituation, effDecision, effOpts, effDesc,
                    // ▼ 렌더 편의 + 단일 패스 힌트(라인 내부 한정)
                    List.copyOf(childrenIds), isRoot, pivotBaseId,
                    pivotSlot, pivotLinkDecisionNodeId,
                    renderPhase, incomingFromId, incomingEdgeType,incomingFromLineId
            );
        }).toList();

        return new DecisionLineDetailDto(
                line.getId(),
                line.getUser().getId(),
                line.getBaseLine().getId(),
                line.getStatus(),
                nodes
        );
    }

    // ===== helpers =====
    private List<String> parseOptionsJson(String optionsJson) {
        if (optionsJson == null || optionsJson.isBlank()) return null;
        try {
            List<String> list = objectMapper.readValue(optionsJson, new TypeReference<List<String>>() {});
            if (list == null) return null;
            List<String> cleaned = list.stream()
                    .filter(s -> s != null && !s.isBlank())
                    .toList();
            return cleaned.isEmpty() ? null : cleaned;
        } catch (Exception ignore) {
            return null; // 파싱 실패 시 안전 폴백
        }
    }

    public List<BaseLineDto> getMyBaseLines(Long userId) {
        return baseLineRepository.findByUser_IdOrderByIdDesc(userId)
                .stream()
                .map(mappers.BASELINE_READ::map)
                .toList();
    }


    // 가장 많이 사용하는 함수 호출 위에 한줄 요약: DECISION parent→children 인덱스 구성
    private Map<Long, List<Long>> buildChildrenIndex(List<DecisionNode> ordered) {
        Map<Long, List<Long>> map = new LinkedHashMap<>();
        for (DecisionNode d : ordered) {
            map.computeIfAbsent(d.getId(), k -> new ArrayList<>());
        }
        for (DecisionNode d : ordered) {
            if (d.getParent() != null) {
                map.get(d.getParent().getId()).add(d.getId());
            }
        }
        return map;
    }



    // 가장 많이 사용하는 함수 호출 위에 한줄 요약: BaseNode의 altOpt1/2TargetDecisionId로 pivot 역인덱스 구성
    private Map<Long, PivotMark> buildPivotIndex(Long baseLineId) {
        Map<Long, PivotMark> index = new HashMap<>();
        List<BaseNode> bases = baseNodeRepository.findByBaseLine_IdOrderByAgeYearAscIdAsc(baseLineId);

        for (BaseNode b : bases) {
            if (b.getAltOpt1TargetDecisionId() != null) {
                index.put(b.getAltOpt1TargetDecisionId(), new PivotMark(b.getId(), 0));
            }
            if (b.getAltOpt2TargetDecisionId() != null) {
                index.put(b.getAltOpt2TargetDecisionId(), new PivotMark(b.getId(), 1));
            }
        }
        return index;
    }

    // 가장 중요한 함수 위에 한줄 요약: 분기 표식 컨테이너(베이스 id와 슬롯 인덱스)
    private record PivotMark(Long baseNodeId, Integer slotIndex) {}

    /*
     * [요약 블럭] 트리 조회 계산용 스냅샷 뷰
     * - 엔티티→DTO 주입 전, 렌더 계산에 필요한 파생값(루트여부, baseId/age, pivot, children)을 묶어 캐시
     * - record 대신 정적 내부 클래스로 정의해 IDE/언어레벨/이름충돌 이슈 제거
     */
    private static final class NodeView {
        private final DecisionNode dn;
        private final DecNodeDto dto;
        private final boolean isRoot;
        private final Long baseId;
        private final Integer age;
        private final List<Long> childrenIds;
        private final Long pivotBaseId;
        private final Integer pivotSlot;

        NodeView(DecisionNode dn, DecNodeDto dto, boolean isRoot,
                 Long baseId, Integer age, List<Long> childrenIds,
                 Long pivotBaseId, Integer pivotSlot) {
            this.dn = dn;
            this.dto = dto;
            this.isRoot = isRoot;
            this.baseId = baseId;
            this.age = age;
            this.childrenIds = childrenIds;
            this.pivotBaseId = pivotBaseId;
            this.pivotSlot = pivotSlot;
        }

        // 원본 엔티티 접근
        DecisionNode dn() { return dn; }
        // DTO 스냅샷 접근
        DecNodeDto dto() { return dto; }

        boolean isRoot() { return isRoot; }
        Long baseId() { return baseId; }
        Integer age() { return age; }
        List<Long> childrenIds() { return childrenIds; }
        Long pivotBaseId() { return pivotBaseId; }
        Integer pivotSlot() { return pivotSlot; }
    }
}
