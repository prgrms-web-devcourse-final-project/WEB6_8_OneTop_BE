/**
 * [QUERY SERVICE] NodeQueryService — 읽기 전용 조회 파사드
 *
 * 흐름 요약
 * - getTreeInfo(userId): 사용자 검증 → BaseNode 전량 조회·정렬 → 각 DecisionLine 노드를 정렬 조회 → DTO 매핑 후 합쳐서 TreeDto 반환
 * - getBaseLineNodes(baseLineId): 라인 존재성 보장 → 정렬된 BaseNode 목록 DTO로 반환
 * - getBaseNode(baseNodeId): 단건 조회 → DTO 매핑
 * - getDecisionLines(userId), getDecisionLineDetail(decisionLineId): 메타/상세 조회(기존 로직 유지)
 */
package com.back.domain.node.service;

import com.back.domain.node.dto.TreeDto;
import com.back.domain.node.dto.base.BaseLineDto;
import com.back.domain.node.dto.base.BaseNodeDto;
import com.back.domain.node.dto.decision.DecNodeDto;
import com.back.domain.node.dto.decision.DecisionLineDetailDto;
import com.back.domain.node.dto.decision.DecisionLineListDto;
import com.back.domain.node.entity.BaseNode;
import com.back.domain.node.entity.DecisionLine;
import com.back.domain.node.entity.DecisionNode;
import com.back.domain.node.mapper.NodeMappers;
import com.back.domain.node.repository.BaseLineRepository;
import com.back.domain.node.repository.BaseNodeRepository;
import com.back.domain.node.repository.DecisionLineRepository;
import com.back.domain.node.repository.DecisionNodeRepository;
import com.back.domain.user.entity.User;
import com.back.domain.user.repository.UserRepository;
import com.back.global.exception.ApiException;
import com.back.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NodeQueryService {

    private final UserRepository userRepository;
    private final BaseNodeRepository baseNodeRepository;
    private final DecisionNodeRepository decisionNodeRepository;
    private final DecisionLineRepository decisionLineRepository;
    private final BaseLineRepository baseLineRepository;
    private final NodeDomainSupport support;

    // 가장 중요한: 특정 BaseLine 전체 트리 조회
    public TreeDto getTreeForBaseLine(Long baseLineId) {
        support.ensureBaseLineExists(baseLineId);

        List<BaseNode> orderedBase = support.getOrderedBaseNodes(baseLineId);
        List<BaseNodeDto> baseDtos = new ArrayList<>(orderedBase.size());
        for (BaseNode n : orderedBase) baseDtos.add(NodeMappers.BASE_READ.map(n));

        List<DecisionLine> lines = decisionLineRepository.findByBaseLine_Id(baseLineId);

        List<DecNodeDto> decDtos = new ArrayList<>();
        for (DecisionLine line : lines) {
            List<DecisionNode> ordered = decisionNodeRepository
                    .findByDecisionLine_IdOrderByAgeYearAscIdAsc(line.getId());
            for (DecisionNode dn : ordered) decDtos.add(NodeMappers.DECISION_READ.map(dn));
        }

        return new TreeDto(baseDtos, decDtos);
    }

    // 라인별 베이스 노드를 정렬하여 반환
    public List<BaseNodeDto> getBaseLineNodes(Long baseLineId) {
        support.ensureBaseLineExists(baseLineId);
        List<BaseNode> nodes = baseNodeRepository.findByBaseLine_IdOrderByAgeYearAscIdAsc(baseLineId);
        if (nodes == null || nodes.isEmpty()) return List.of();
        List<BaseNodeDto> result = new ArrayList<>(nodes.size());
        for (BaseNode n : nodes) result.add(NodeMappers.BASE_READ.map(n));
        return result;
    }

    // BaseNode 단건 조회
    public BaseNodeDto getBaseNode(Long baseNodeId) {
        BaseNode node = baseNodeRepository.findById(baseNodeId)
                .orElseThrow(() -> new ApiException(ErrorCode.NODE_NOT_FOUND, "BaseNode not found: " + baseNodeId));
        return NodeMappers.BASE_READ.map(node);
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

    // 특정 결정 라인의 상세(정렬된 노드 포함)
    public DecisionLineDetailDto getDecisionLineDetail(Long decisionLineId) {
        DecisionLine line = decisionLineRepository.findById(decisionLineId)
                .orElseThrow(() -> new ApiException(ErrorCode.DECISION_LINE_NOT_FOUND, "DecisionLine not found: " + decisionLineId));

        List<DecisionNode> nodes = decisionNodeRepository.findByDecisionLine_IdOrderByAgeYearAscIdAsc(decisionLineId);
        List<DecNodeDto> nodeDtos = new ArrayList<>(nodes.size());
        for (DecisionNode n : nodes) nodeDtos.add(NodeMappers.DECISION_READ.map(n));

        return new DecisionLineDetailDto(
                line.getId(),
                line.getUser().getId(),
                line.getBaseLine().getId(),
                line.getStatus(),
                nodeDtos
        );
    }

    public List<BaseLineDto> getMyBaseLines(Long userId) {
        return baseLineRepository.findByUser_IdOrderByIdDesc(userId)
                .stream()
                .map(NodeMappers.BASELINE_READ::map)
                .toList();
    }
}
