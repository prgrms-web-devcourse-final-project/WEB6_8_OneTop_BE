/**
 * NodeQueryService
 * - 트리 조회, 라인 노드 목록, 단건 노드 조회(읽기 전용)
 */
package com.back.domain.node.service;

import com.back.domain.node.dto.BaseNodeDto;
import com.back.domain.node.dto.DecLineDto;
import com.back.domain.node.dto.TreeDto;
import com.back.domain.node.entity.BaseNode;
import com.back.domain.node.entity.DecisionLine;
import com.back.domain.node.entity.DecisionNode;
import com.back.domain.node.mapper.NodeMappers;
import com.back.domain.node.repository.BaseNodeRepository;
import com.back.domain.node.repository.DecisionLineRepository;
import com.back.domain.user.entity.User;
import com.back.domain.user.repository.UserRepository;
import com.back.global.exception.ApiException;
import com.back.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
class NodeQueryService {

    private final UserRepository userRepository;
    private final BaseNodeRepository baseNodeRepository;
    private final DecisionLineRepository decisionLineRepository;
    private final NodeDomainSupport support;

    // 가장 중요한: 트리 전체 조회
    public TreeDto getTreeInfo(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND, "User not found: " + userId));

        List<BaseNodeDto> baseDtos = new ArrayList<>();
        for (BaseNode n : baseNodeRepository.findByUser(user)) baseDtos.add(NodeMappers.BASE_READ.map(n));

        List<DecLineDto> decDtos = new ArrayList<>();
        for (DecisionLine line : decisionLineRepository.findByUser(user))
            for (DecisionNode dn : line.getDecisionNodes()) decDtos.add(NodeMappers.DECISION_READ.map(dn));

        return new TreeDto(baseDtos, decDtos);
    }

    // 가장 많이 사용하는: BaseLine 노드 정렬 조회(나이 asc)
    public List<BaseNodeDto> getBaseLineNodes(Long baseLineId) {
        support.ensureBaseLineExists(baseLineId);
        List<BaseNode> nodes = baseNodeRepository.findByBaseLine_IdOrderByAgeYearAscIdAsc(baseLineId);
        if (nodes == null || nodes.isEmpty()) return List.of();
        List<BaseNodeDto> result = new ArrayList<>(nodes.size());
        for (BaseNode n : nodes) result.add(NodeMappers.BASE_READ.map(n));
        return result;
    }

    // 가장 많이 사용하는: BaseNode 단건 조회
    public BaseNodeDto getBaseNode(Long baseNodeId) {
        BaseNode node = baseNodeRepository.findById(baseNodeId)
                .orElseThrow(() -> new ApiException(ErrorCode.NODE_NOT_FOUND, "BaseNode not found: " + baseNodeId));
        return NodeMappers.BASE_READ.map(node);
    }
}
