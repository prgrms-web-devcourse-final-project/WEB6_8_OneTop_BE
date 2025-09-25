/**
 * NodeService (파사드)
 * - 컨트롤러에서 사용하는 퍼블릭 API를 그대로 유지하고, 실제 로직은 서브서비스로 위임
 * - BaseLineService / DecisionFlowService / NodeQueryService 로 관심사 분리
 */
package com.back.domain.node.service;

import com.back.domain.node.dto.*;
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
    public DecLineDto createDecisionNodeFromBase(DecisionNodeFromBaseRequest request) {
        return decisionFlowService.createDecisionNodeFromBase(request);
    }

    // next 생성 위임
    public DecLineDto createDecisionNodeNext(DecisionNodeNextRequest request) {
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
}
