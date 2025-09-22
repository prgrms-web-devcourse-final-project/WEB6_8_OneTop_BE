/**
 * NodeService (매퍼 중심 사용)
 * - write: 요청DTO → 매퍼.toEntity → save → 매퍼.toResponse
 * - read : 엔티티 → 전역 Mapper(S->T)로 DTO 변환 (정적 from 미사용)
 * - 에러: 도메인 런타임 예외 → ApiException(INVALID_INPUT_VALUE)로 일괄 매핑, 존재하지 않는 리소스는 *_NOT_FOUND
 * - 배경문: 현재는 situation 그대로 사용. 나중에 AI 붙일 때 resolveBackground(...) 내부만 교체.
 */
package com.back.domain.node.service;

import com.back.domain.node.dto.BaseLineBulkCreateRequest;
import com.back.domain.node.dto.BaseLineBulkCreateResponse;
import com.back.domain.node.dto.BaseLineDto;
import com.back.domain.node.dto.DecLineDto;
import com.back.domain.node.dto.DecisionLineLifecycleDto;
import com.back.domain.node.dto.DecisionNodeFromBaseRequest;
import com.back.domain.node.dto.DecisionNodeNextRequest;
import com.back.domain.node.dto.PivotListDto;
import com.back.domain.node.dto.TreeDto;
import com.back.domain.node.dto.DecisionNodeCreateRequestDto;
import com.back.domain.node.entity.BaseLine;
import com.back.domain.node.entity.BaseNode;
import com.back.domain.node.entity.DecisionLine;
import com.back.domain.node.entity.DecisionLineStatus;
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
import java.util.List;
import java.util.Objects;

@Service
@Transactional
@RequiredArgsConstructor
public class NodeService {

    private final BaseLineRepository baseLineRepository;
    private final BaseNodeRepository baseNodeRepository;
    private final DecisionLineRepository decisionLineRepository;
    private final DecisionNodeRepository decisionNodeRepository;
    private final UserRepository userRepository;

    // 트리 전체 조회: 엔티티 → 전역 읽기 매퍼로 DTO 변환
    public TreeDto getTreeInfo(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND, "User not found: " + userId));

        List<BaseLineDto> baseDtos = new ArrayList<>();
        for (BaseNode n : baseNodeRepository.findByUser(user)) {
            baseDtos.add(NodeMappers.BASE_READ.map(n));
        }

        List<DecLineDto> decDtos = new ArrayList<>();
        for (DecisionLine line : decisionLineRepository.findByUser(user)) {
            for (DecisionNode dn : line.getDecisionNodes()) {
                decDtos.add(NodeMappers.DECISION_READ.map(dn));
            }
        }
        return new TreeDto(baseDtos, decDtos);
    }

    // BaseLine 일괄 생성: payload → 매퍼 → save → 매퍼.toResponse
    public BaseLineBulkCreateResponse createBaseLineWithNodes(BaseLineBulkCreateRequest request) {
        if (request == null || request.nodes() == null || request.nodes().isEmpty()) {
            throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "nodes must not be empty");
        }
        if (request.nodes().size() < 2) {
            throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "nodes length must be >= 2 (header and tail required)");
        }

        User user = (request.userId() != null)
                ? userRepository.findById(request.userId())
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND, "User not found: " + request.userId()))
                : null;

        BaseLine baseLine = baseLineRepository.save(BaseLine.builder().user(user).build());

        BaseNode prev = null;
        List<BaseLineBulkCreateResponse.CreatedNode> created = new ArrayList<>();

        for (int i = 0; i < request.nodes().size(); i++) {
            BaseLineBulkCreateRequest.BaseNodePayload payload = request.nodes().get(i);
            NodeMappers.BaseNodeCtxMapper mapper = new NodeMappers.BaseNodeCtxMapper(user, baseLine, prev);
            BaseNode saved = baseNodeRepository.save(mapper.toEntity(payload));
            created.add(new BaseLineBulkCreateResponse.CreatedNode(i, saved.getId()));
            prev = saved;
        }
        return new BaseLineBulkCreateResponse(baseLine.getId(), created);
    }

    // 피벗 목록 조회(헤더/꼬리 제외)
    public PivotListDto getPivotBaseNodes(Long baseLineId) {
        List<BaseNode> ordered = getOrderedBaseNodes(baseLineId);
        if (ordered.size() <= 2) return new PivotListDto(baseLineId, List.of());
        List<PivotListDto.PivotDto> list = new ArrayList<>();
        for (int i = 1; i < ordered.size() - 1; i++) {
            BaseNode n = ordered.get(i);
            list.add(new PivotListDto.PivotDto(i, n.getId(), n.getCategory(), n.getSituation(), n.getAgeYear()));
        }
        return new PivotListDto(baseLineId, list);
    }

    // 첫 DecisionNode 생성(피벗에서): 규칙 검증 → 배경 생성(현재는 pass-through) → 매퍼로 생성
    public DecLineDto createDecisionNodeFromBase(DecisionNodeFromBaseRequest request) {
        if (request == null || request.baseLineId() == null || request.pivotBaseNodeId() == null) {
            throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "baseLineId and pivotBaseNodeId are required");
        }

        BaseLine baseLine = baseLineRepository.findById(request.baseLineId())
                .orElseThrow(() -> new ApiException(ErrorCode.BASE_LINE_NOT_FOUND, "BaseLine not found: " + request.baseLineId()));
        BaseNode pivot = baseNodeRepository.findById(request.pivotBaseNodeId())
                .orElseThrow(() -> new ApiException(ErrorCode.NODE_NOT_FOUND, "BaseNode (pivot) not found: " + request.pivotBaseNodeId()));

        List<BaseNode> ordered = getOrderedBaseNodes(baseLine.getId());
        int pivotIdx = indexOfNode(ordered, pivot.getId());
        if (pivotIdx <= 0 || pivotIdx >= ordered.size() - 1) {
            throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "pivot must be one of middle nodes (not header/tail)");
        }

        int pivotAge = pivot.getAgeYear();
        int age = request.ageYear() != null ? request.ageYear() : pivotAge;
        if (age != pivotAge) {
            throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "first decision ageYear must equal pivot ageYear");
        }

        DecisionLine line = decisionLineRepository.save(
                DecisionLine.builder()
                        .user(baseLine.getUser())
                        .baseLine(baseLine)
                        .status(DecisionLineStatus.DRAFT)
                        .build()
        );

        String situation = request.situation() != null ? request.situation() : pivot.getSituation();
        String background = resolveBackground(situation); // 현재는 situation 그대로

        NodeMappers.DecisionNodeCtxMapper mapper =
                new NodeMappers.DecisionNodeCtxMapper(baseLine.getUser(), line, null, pivot, background);

        DecisionNodeCreateRequestDto createReq = new DecisionNodeCreateRequestDto(
                line.getId(), null, pivot.getId(),
                request.category() != null ? request.category() : pivot.getCategory(),
                situation,
                request.decision(), age
        );

        DecisionNode saved = decisionNodeRepository.save(mapper.toEntity(createReq));
        return mapper.toResponse(saved);
    }

    // 다음 DecisionNode 생성(연속): 다음 피벗 나이 자동 선택 지원 + 배경(pass-through)
    public DecLineDto createDecisionNodeNext(DecisionNodeNextRequest request) {
        if (request == null || request.parentDecisionNodeId() == null) {
            throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "parentDecisionNodeId is required");
        }

        DecisionNode parent = decisionNodeRepository.findById(request.parentDecisionNodeId())
                .orElseThrow(() -> new ApiException(ErrorCode.NODE_NOT_FOUND, "Parent DecisionNode not found: " + request.parentDecisionNodeId()));

        DecisionLine line = (request.decisionLineId() != null)
                ? decisionLineRepository.findById(request.decisionLineId())
                .orElseThrow(() -> new ApiException(ErrorCode.DECISION_LINE_NOT_FOUND, "DecisionLine not found: " + request.decisionLineId()))
                : parent.getDecisionLine();

        if (line.getStatus() == DecisionLineStatus.COMPLETED || line.getStatus() == DecisionLineStatus.CANCELLED) {
            throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "cannot append to a completed or cancelled decision line");
        }

        BaseLine baseLine = line.getBaseLine();
        List<BaseNode> ordered = getOrderedBaseNodes(baseLine.getId());
        List<Integer> pivotAges = allowedPivotAges(ordered);

        int parentAge = parent.getAgeYear();
        Integer nextAge = request.ageYear();
        if (nextAge == null) {
            nextAge = pivotAges.stream().filter(a -> a > parentAge).findFirst()
                    .orElseThrow(() -> new ApiException(ErrorCode.INVALID_INPUT_VALUE, "no more pivot ages available"));
        }
        if (!pivotAges.contains(nextAge) || nextAge <= parentAge) {
            throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "invalid next pivot age");
        }

        int maxPivot = pivotAges.get(pivotAges.size() - 1);
        if (nextAge > maxPivot) {
            throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "ageYear cannot exceed last pivot age of base line");
        }

        for (DecisionNode d : line.getDecisionNodes()) {
            if (d.getAgeYear() == nextAge) {
                throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "a decision node already exists at this pivot age");
            }
        }

        BaseNode matchedBase = null;
        for (BaseNode b : ordered) {
            if (b.getAgeYear() == nextAge) { matchedBase = b; break; }
        }

        String situation = request.situation() != null ? request.situation() : parent.getSituation();
        String background = resolveBackground(situation); // 현재는 situation 그대로

        NodeMappers.DecisionNodeCtxMapper mapper =
                new NodeMappers.DecisionNodeCtxMapper(parent.getUser(), line, parent, matchedBase, background);

        DecisionNodeCreateRequestDto createReq = new DecisionNodeCreateRequestDto(
                line.getId(), parent.getId(), matchedBase != null ? matchedBase.getId() : null,
                request.category() != null ? request.category() : parent.getCategory(),
                situation,
                request.decision(), nextAge
        );

        DecisionNode saved = decisionNodeRepository.save(mapper.toEntity(createReq));
        return mapper.toResponse(saved);
    }

    // 결정 라인 취소
    public DecisionLineLifecycleDto cancelDecisionLine(Long decisionLineId) {
        DecisionLine line = requireDecisionLine(decisionLineId);
        try {
            line.cancel();
        } catch (RuntimeException e) {
            throw mapDomainToApi(e);
        }
        decisionLineRepository.save(line);
        return new DecisionLineLifecycleDto(line.getId(), line.getStatus());
    }

    // 결정 라인 완료
    public DecisionLineLifecycleDto completeDecisionLine(Long decisionLineId) {
        DecisionLine line = requireDecisionLine(decisionLineId);
        try {
            line.complete();
        } catch (RuntimeException e) {
            throw mapDomainToApi(e);
        }
        decisionLineRepository.save(line);
        return new DecisionLineLifecycleDto(line.getId(), line.getStatus());
    }

    // BaseLine 노드 정렬 조회(나이 asc)
    private List<BaseNode> getOrderedBaseNodes(Long baseLineId) {
        BaseLine baseLine = baseLineRepository.findById(baseLineId)
                .orElseThrow(() -> new ApiException(ErrorCode.BASE_LINE_NOT_FOUND, "BaseLine not found: " + baseLineId));
        List<BaseNode> nodes = baseNodeRepository.findByBaseLine_IdOrderByAgeYearAscIdAsc(baseLine.getId());
        return nodes == null ? List.of() : nodes;
    }

    // id 인덱스 찾기
    private int indexOfNode(List<BaseNode> ordered, Long baseNodeId) {
        for (int i = 0; i < ordered.size(); i++) {
            if (Objects.equals(ordered.get(i).getId(), baseNodeId)) return i;
        }
        return -1;
    }

    // 허용 피벗 나이 목록(헤더/꼬리 제외, 중복 제거)
    private List<Integer> allowedPivotAges(List<BaseNode> ordered) {
        if (ordered.size() <= 2) return List.of();
        List<Integer> ages = new ArrayList<>();
        for (int i = 1; i < ordered.size() - 1; i++) {
            ages.add(ordered.get(i).getAgeYear());
        }
        // distinct + 오름차순
        List<Integer> distinctSorted = new ArrayList<>();
        ages.stream().sorted().forEach(a -> {
            if (distinctSorted.isEmpty() || !distinctSorted.get(distinctSorted.size() - 1).equals(a)) {
                distinctSorted.add(a);
            }
        });
        return distinctSorted;
    }

    // BaseLine 노드 목록 조회: 엔티티 → 전역 읽기 매퍼
    public List<BaseLineDto> getBaseLineNodes(Long baseLineId) {
        List<BaseNode> nodes = baseNodeRepository.findByBaseLine_IdOrderByAgeYearAscIdAsc(baseLineId);
        if (nodes == null || nodes.isEmpty()) return List.of();
        List<BaseLineDto> result = new ArrayList<>(nodes.size());
        for (BaseNode n : nodes) result.add(NodeMappers.BASE_READ.map(n));
        return result;
    }

    // BaseNode 단건 조회: 엔티티 → 전역 읽기 매퍼
    public BaseLineDto getBaseNode(Long baseNodeId) {
        BaseNode node = baseNodeRepository.findById(baseNodeId)
                .orElseThrow(() -> new ApiException(ErrorCode.NODE_NOT_FOUND, "BaseNode not found: " + baseNodeId));
        return NodeMappers.BASE_READ.map(node);
    }

    // 도메인 런타임 예외 → ApiException(INVALID_INPUT_VALUE)로 매핑
    private RuntimeException mapDomainToApi(RuntimeException e) {
        return new ApiException(ErrorCode.INVALID_INPUT_VALUE, e.getMessage());
    }

    // DecisionLine 필수 조회
    private DecisionLine requireDecisionLine(Long decisionLineId) {
        return decisionLineRepository.findById(decisionLineId)
                .orElseThrow(() -> new ApiException(ErrorCode.DECISION_LINE_NOT_FOUND, "DecisionLine not found: " + decisionLineId));
    }

    // --- 배경 생성 훅: 지금은 pass-through. 나중에 AI 붙일 때 이 내부만 수정 ---
    private String resolveBackground(String situation) {
        return situation == null ? "" : situation;
    }
}
