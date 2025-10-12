/**
 * NodeService (파사드)
 * - 컨트롤러에서 사용하는 퍼블릭 API를 그대로 유지하고, 실제 로직은 서브서비스로 위임
 * - BaseLineService / DecisionFlowService / NodeQueryService 로 관심사 분리
 */
package com.back.domain.node.service;

import com.back.domain.node.dto.*;
import com.back.domain.node.dto.base.BaseLineBulkCreateRequest;
import com.back.domain.node.dto.base.BaseLineBulkCreateResponse;
import com.back.domain.node.dto.base.BaseLineDto;
import com.back.domain.node.dto.base.BaseNodeDto;
import com.back.domain.node.dto.decision.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class NodeService {

    private final BaseLineService baseLineService;
    private final DecisionFlowService decisionFlowService;
    private final NodeQueryService nodeQueryService;

    // 트리 전체 조회 위임
    public TreeDto getTreeForBaseLine(Long baseLineId) {
        return nodeQueryService.getTreeForBaseLine(baseLineId);
    }

    // BaseLine 일괄 생성 위임
    public BaseLineBulkCreateResponse createBaseLineWithNodes(BaseLineBulkCreateRequest request) {
        return baseLineService.createBaseLineWithNodes(request);
    }

    // 피벗 목록 조회 위임
    public PivotListDto getPivotBaseNodes(Long baseLineId) {
        return baseLineService.getPivotBaseNodes(baseLineId);
    }

    // from-base 생성 위임
    public DecNodeDto createDecisionNodeFromBase(DecisionNodeFromBaseRequest request) {
        return decisionFlowService.createDecisionNodeFromBase(request);
    }

    // next 생성 위임
    public DecNodeDto createDecisionNodeNext(DecisionNodeNextRequest request) {
        return decisionFlowService.createDecisionNodeNext(request);
    }

    // 라인 취소 위임
    public DecisionLineLifecycleDto cancelDecisionLine(Long decisionLineId) {
        return decisionFlowService.cancelDecisionLine(decisionLineId);
    }

    // 라인 완료 위임
    public DecisionLineLifecycleDto completeDecisionLine(Long decisionLineId) {
        return decisionFlowService.completeDecisionLine(decisionLineId);
    }

    // BaseLine 노드 정렬 조회 위임
    public List<BaseNodeDto> getBaseLineNodes(Long baseLineId) {
        return nodeQueryService.getBaseLineNodes(baseLineId);
    }

    // BaseNode 단건 조회 위임
    public BaseNodeDto getBaseNode(Long baseNodeId) {
        return nodeQueryService.getBaseNode(baseNodeId);
    }

    // 가장 중요한: 결정 노드에서 세계선 포크
    public DecNodeDto forkFromDecision(ForkFromDecisionRequest request) {
        return decisionFlowService.forkFromDecision(request);
    }

    public List<BaseLineDto> getMyBaseLines(Long id) {
        return nodeQueryService.getMyBaseLines(id);
    }

    // 소유자 검증 포함 베이스라인 깊은 삭제 위임
    public void deleteBaseLineDeep(Long userId, Long baseLineId) {
        baseLineService.deleteBaseLineDeep(userId, baseLineId);
    }
}
