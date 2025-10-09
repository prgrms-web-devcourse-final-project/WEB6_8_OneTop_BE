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

    // 가장 많이 사용하는: 특정 BaseLine 전체 트리에도 동일한 편의 필드 주입
    public TreeDto getTreeForBaseLine(Long baseLineId) {
        support.ensureBaseLineExists(baseLineId);

        List<BaseNode> orderedBase = support.getOrderedBaseNodes(baseLineId);
        List<BaseNodeDto> baseDtos = orderedBase.stream().map(mappers.BASE_READ::map).toList();

        // 전체 라인 공용 pivot 역인덱스
        Map<Long, PivotMark> pivotIndex = buildPivotIndex(baseLineId);

        List<DecNodeDto> decDtos = new ArrayList<>();
        List<DecisionLine> lines = decisionLineRepository.findByBaseLine_Id(baseLineId);

        for (DecisionLine line : lines) {
            List<DecisionNode> ordered = decisionNodeRepository
                    .findByDecisionLine_IdOrderByAgeYearAscIdAsc(line.getId());

            Map<Long, List<Long>> childrenIndex = buildChildrenIndex(ordered);

            for (DecisionNode dn : ordered) {
                DecNodeDto base = mappers.DECISION_READ.map(dn);

                List<Long> childrenIds = childrenIndex.getOrDefault(dn.getId(), List.of());
                boolean isRoot = (dn.getParent() == null);

                PivotMark mark = pivotIndex.get(base.id());
                Long pivotBaseId = (mark != null) ? mark.baseNodeId() : null;
                Integer pivotSlot = (mark != null) ? mark.slotIndex() : null;

                // effective*는 DECISION_READ에서 이미 계산/주입되어 있다면 그대로 사용
                decDtos.add(new DecNodeDto(
                        base.id(), base.userId(), base.type(), base.category(),
                        base.situation(), base.decision(), base.ageYear(),
                        base.decisionLineId(), base.parentId(), base.baseNodeId(),
                        base.background(), base.options(), base.selectedIndex(),
                        base.parentOptionIndex(), base.description(),
                        base.aiNextSituation(), base.aiNextRecommendedOption(),
                        base.followPolicy(), base.pinnedCommitId(), base.virtual(),
                        base.effectiveCategory(), base.effectiveSituation(), base.effectiveDecision(),
                        base.effectiveOptions(), base.effectiveDescription(),
                        // ▼ 렌더 편의 필드
                        List.copyOf(childrenIds), isRoot, pivotBaseId, pivotSlot
                ));
            }
        }
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
                .orElseThrow(() -> new ApiException(ErrorCode.DECISION_LINE_NOT_FOUND, "DecisionLine not found: " + decisionLineId));

        Long baseLineId   = line.getBaseLine().getId();
        Long baseBranchId = (line.getBaseBranch() != null) ? line.getBaseBranch().getId() : null;

        // 가장 많이 사용하는: 라인 노드를 타임라인 정렬 조회
        List<DecisionNode> ordered = decisionNodeRepository
                .findByDecisionLine_IdOrderByAgeYearAscIdAsc(line.getId());

        // parent→children 인덱스 구성
        Map<Long, List<Long>> childrenIndex = buildChildrenIndex(ordered);
        // 베이스 분기 슬롯 역인덱스 구성(altOpt1/2TargetDecisionId → (baseNodeId, slot))
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
                    if (v.getCategory()   != null) effCategory  = v.getCategory();
                    if (v.getSituation()  != null) effSituation = v.getSituation();
                    if (v.getDecision()   != null) effDecision  = v.getDecision();
                    List<String> parsed = parseOptionsJson(v.getOptionsJson());
                    if (parsed != null)           effOpts      = parsed;
                    if (v.getDescription() != null) effDesc     = v.getDescription();
                }
            }

            List<Long> childrenIds = childrenIndex.getOrDefault(n.getId(), List.of());
            boolean isRoot = (n.getParent() == null);

            PivotMark mark = pivotIndex.get(base.id());
            Long pivotBaseId = (mark != null) ? mark.baseNodeId() : null;
            Integer pivotSlot = (mark != null) ? mark.slotIndex() : null;

            return new DecNodeDto(
                    base.id(), base.userId(), base.type(), base.category(),
                    base.situation(), base.decision(), base.ageYear(),
                    base.decisionLineId(), base.parentId(), base.baseNodeId(),
                    base.background(), base.options(), base.selectedIndex(),
                    base.parentOptionIndex(), base.description(),
                    base.aiNextSituation(), base.aiNextRecommendedOption(),
                    base.followPolicy(), base.pinnedCommitId(), base.virtual(),
                    // effective*
                    effCategory, effSituation, effDecision, effOpts, effDesc,
                    // ▼ 렌더 편의 필드
                    List.copyOf(childrenIds), isRoot, pivotBaseId, pivotSlot
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
