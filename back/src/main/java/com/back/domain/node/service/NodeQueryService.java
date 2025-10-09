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
import java.util.stream.Collectors;

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

    // 가장 많이 사용하는: 특정 BaseLine 전체 트리에도 동일한 편의 필드 주입
    public TreeDto getTreeForBaseLine(Long baseLineId) {
        support.ensureBaseLineExists(baseLineId);

        List<BaseNode> orderedBase = support.getOrderedBaseNodes(baseLineId);
        List<BaseNodeDto> baseDtos = orderedBase.stream().map(mappers.BASE_READ::map).toList();

        // 가장 많이 사용하는: 전체 라인 공용 pivot 역인덱스
        Map<Long, PivotMark> pivotIndex = buildPivotIndex(baseLineId);

        // ===== (1) 노드 수집 =====
        record Key(Long baseId, Integer age) {}
        record View(DecisionNode dn, DecNodeDto dto, boolean isRoot, Long baseId, Integer age,
                    List<Long> childrenIds, Long pivotBaseId, Integer pivotSlot) {}

        List<DecNodeDto> decDtos = new ArrayList<>();
        List<DecisionLine> lines = decisionLineRepository.findByBaseLine_Id(baseLineId);

        List<View> pool = new ArrayList<>();
        Map<Long, List<View>> byLine = new HashMap<>();

        for (DecisionLine line : lines) {
            // 가장 많이 사용하는 함수 호출 위에 한줄로만 요약 주석: 라인의 노드를 타임라인 정렬로 로드
            List<DecisionNode> ordered = decisionNodeRepository
                    .findByDecisionLine_IdOrderByAgeYearAscIdAsc(line.getId());

            Map<Long, List<Long>> childrenIndex = buildChildrenIndex(ordered);

            for (DecisionNode dn : ordered) {
                DecNodeDto base = mappers.DECISION_READ.map(dn);

                boolean isRoot = (dn.getParent() == null);
                List<Long> childrenIds = childrenIndex.getOrDefault(dn.getId(), List.of());

                PivotMark mark = pivotIndex.get(base.id());
                Long pivotBaseId = (mark != null) ? mark.baseNodeId() :
                        (dn.getBaseNode() != null ? dn.getBaseNode().getId() : null);
                Integer pivotSlot = (mark != null) ? mark.slotIndex() : null;

                Long baseId = pivotBaseId;
                Integer age = dn.getAgeYear();

                View v = new View(dn, base, isRoot, baseId, age, List.copyOf(childrenIds), pivotBaseId, pivotSlot);
                pool.add(v);
                byLine.computeIfAbsent(line.getId(), k -> new ArrayList<>()).add(v);
            }
        }

        Map<Key, List<View>> byKey = pool.stream()
                .filter(v -> v.baseId != null && v.age != null)
                .collect(Collectors.groupingBy(v -> new Key(v.baseId, v.age)));

        // ===== (2) 라인 간 포크 그래프 추론 → renderPhase 계산 =====
        Map<Long, View> rootCand = new HashMap<>(); // lineId -> 루트 후보
        for (Map.Entry<Long, List<View>> e : byLine.entrySet()) {
            List<View> vs = e.getValue().stream()
                    .sorted(Comparator.comparing((View x) -> x.age)
                            .thenComparing(x -> x.dn.getId()))
                    .toList();

            View first = vs.get(0);
            // 가장 중요한 함수: 라인 루트가 헤더면 다음 피벗을 루트 후보로 대체
            View cand = (first.isRoot && first.baseId == null && vs.size() > 1) ? vs.get(1) : first;
            rootCand.put(e.getKey(), cand);
        }

        Map<Long, Set<Long>> g = new HashMap<>();   // originLineId -> {forkLineId}
        Map<Long, Integer> indeg = new HashMap<>(); // lineId -> indegree

        for (Map.Entry<Long, View> e : rootCand.entrySet()) {
            Long lineId = e.getKey();
            View me = e.getValue();
            indeg.putIfAbsent(lineId, 0);

            if (me.baseId != null && me.age != null) {
                List<View> same = byKey.getOrDefault(new Key(me.baseId, me.age), List.of());
                Optional<View> origin = same.stream()
                        .filter(o -> !o.dn.getDecisionLine().getId().equals(lineId))
                        .sorted(Comparator
                                .comparing((View o) -> o.dn.getDecisionLine().getId())
                                .thenComparing(o -> o.dn.getId()))
                        .findFirst();

                if (origin.isPresent()) {
                    Long originLineId = origin.get().dn.getDecisionLine().getId();
                    g.computeIfAbsent(originLineId, k -> new HashSet<>()).add(lineId);
                    indeg.put(lineId, indeg.getOrDefault(lineId, 0) + 1);
                    indeg.putIfAbsent(originLineId, 0);
                }
            }
        }

        Map<Long, Integer> linePhase = new HashMap<>(); // lineId -> phase
        ArrayDeque<Long> q = new ArrayDeque<>();
        for (Map.Entry<Long, Integer> e : indeg.entrySet()) {
            if (e.getValue() == 0) { // indegree==0 → from-base
                linePhase.put(e.getKey(), 1);
                q.add(e.getKey());
            }
        }
        while (!q.isEmpty()) {
            Long u = q.poll();
            int next = linePhase.get(u) + 1;
            for (Long v : g.getOrDefault(u, Set.of())) {
                indeg.put(v, indeg.get(v) - 1);
                linePhase.put(v, Math.max(linePhase.getOrDefault(v, 1), next));
                if (indeg.get(v) == 0) q.add(v);
            }
        }

        // ===== (3) DTO 주입: renderPhase + incomingFromId/incomingEdgeType =====
        for (View v : pool) {
            DecNodeDto b = v.dto;

            Long lineId = v.dn.getDecisionLine().getId();
            Integer renderPhase = linePhase.getOrDefault(lineId, 1);

            Long incomingFromId = (v.isRoot)
                    ? byKey.getOrDefault(new Key(v.baseId, v.age), List.of()).stream()
                    .filter(o -> !o.dn.getDecisionLine().getId().equals(lineId))
                    .sorted(Comparator
                            .comparing((View o) -> o.dn.getDecisionLine().getId())
                            .thenComparing(o -> o.dn.getId()))
                    .map(o -> o.dn.getId())
                    .findFirst()
                    .orElse(null)
                    : (v.dn.getParent() != null ? v.dn.getParent().getId() : null);

            String incomingEdgeType = (v.isRoot && incomingFromId != null) ? "fork" : "normal";

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
                    v.childrenIds, v.isRoot, v.pivotBaseId, v.pivotSlot,
                    renderPhase, incomingFromId, incomingEdgeType
            ));
        }

        // 출력 순서 보장: phase → ageYear → id
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
                    List.copyOf(childrenIds), isRoot, pivotBaseId, pivotSlot,
                    renderPhase, incomingFromId, incomingEdgeType
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
}
