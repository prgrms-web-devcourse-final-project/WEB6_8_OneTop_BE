package com.back.domain.scenario.service;

import com.back.domain.node.dto.decision.DecisionNodeNextRequest;
import com.back.domain.node.entity.*;
import com.back.domain.node.repository.BaseLineRepository;
import com.back.domain.node.repository.DecisionLineRepository;
import com.back.domain.node.repository.DecisionNodeRepository;
import com.back.domain.node.service.DecisionFlowService;
import com.back.domain.scenario.dto.*;
import com.back.domain.scenario.entity.Scenario;
import com.back.domain.scenario.entity.ScenarioStatus;
import com.back.domain.scenario.entity.SceneCompare;
import com.back.domain.scenario.entity.SceneType;
import com.back.domain.scenario.repository.ScenarioRepository;
import com.back.domain.scenario.repository.SceneCompareRepository;
import com.back.domain.scenario.repository.SceneTypeRepository;
import com.back.global.common.PageResponse;
import com.back.global.exception.ApiException;
import com.back.global.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 시나리오 관련 비즈니스 로직을 처리하는 서비스.
 * 시나리오 생성, 상세 조회, 비교 등의 기능을 제공합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScenarioService {

    // Repository 주입
    private final ScenarioRepository scenarioRepository;
    private final SceneTypeRepository sceneTypeRepository;
    private final SceneCompareRepository sceneCompareRepository;

    // Node Repository 주입
    private final DecisionLineRepository decisionLineRepository;
    private final BaseLineRepository baseLineRepository;
    private final DecisionNodeRepository decisionNodeRepository;

    // Object Mapper 주입
    private final ObjectMapper objectMapper;

    // Scenario Transaction Service 주입
    private final ScenarioTransactionService scenarioTransactionService;

    // 노드 서비스 추가(시나리오 생성과 동시에 마지막 노드 처리용)
    private final DecisionFlowService decisionFlowService;

    /**
     * 시나리오 생성 요청 처리.
     * 트랜잭션을 최소화하기 위해 검증 → 생성 → 비동기 트리거 순서로 분리.
     */
    public ScenarioStatusResponse createScenario(Long userId,
                                                 ScenarioCreateRequest request,
                                                 @Nullable DecisionNodeNextRequest lastDecision) {
        // 1. 검증 및 기존 시나리오 확인 (읽기 전용)
        ScenarioValidationResult validationResult = validateScenarioCreation(userId, request, lastDecision);

        if (validationResult.existingScenario != null) {
            // 기존 시나리오가 있으면 상태에 따라 처리
            return handleExistingScenario(validationResult.existingScenario);
        }

        // 2. 시나리오 생성 (짧은 트랜잭션)
        Long scenarioId = createScenarioInTransaction(
            userId,
            request,
            lastDecision,
            validationResult.decisionLine
        );

        // 3. 비동기 AI 처리 트리거 (트랜잭션 외부, 별도 Bean에서 호출)
        scenarioTransactionService.processScenarioGenerationAsync(scenarioId);

        return new ScenarioStatusResponse(
            scenarioId,
            ScenarioStatus.PENDING,
            "시나리오 생성이 시작되었습니다."
        );
    }

    // 시나리오 생성 요청 검증
    private ScenarioValidationResult validateScenarioCreation(
            Long userId,
            ScenarioCreateRequest request,
            @Nullable DecisionNodeNextRequest lastDecision) {

        // DecisionLine 존재 여부 확인 (User EAGER 로딩)
        DecisionLine decisionLine = decisionLineRepository.findWithUserById(request.decisionLineId())
                .orElseThrow(() -> new ApiException(ErrorCode.DECISION_LINE_NOT_FOUND));

        // 권한 검증
        if (!decisionLine.getUser().getId().equals(userId)) {
            throw new ApiException(ErrorCode.HANDLE_ACCESS_DENIED);
        }

        ensureOwnerEditable(userId, decisionLine);

        // lastDecision 검증
        if (lastDecision != null) {
            ensureSameLine(decisionLine, lastDecision);
        }

        // 기존 시나리오 확인 (Unique Constraint로 동시성 제어)
        Optional<Scenario> existingScenario = scenarioRepository
                .findByDecisionLineId(request.decisionLineId());

        return new ScenarioValidationResult(decisionLine, existingScenario.orElse(null));
    }

    /**
     * 기존 시나리오 처리 로직
     */
    private ScenarioStatusResponse handleExistingScenario(Scenario existing) {
        ScenarioStatus status = existing.getStatus();

        // PENDING/PROCESSING 상태면 중복 생성 방지
        if (status == ScenarioStatus.PENDING || status == ScenarioStatus.PROCESSING) {
            throw new ApiException(ErrorCode.SCENARIO_ALREADY_IN_PROGRESS,
                    "해당 선택 경로의 시나리오가 이미 생성 중입니다.");
        }

        // FAILED 상태면 재시도 로직
        if (status == ScenarioStatus.FAILED) {
            return handleFailedScenarioRetry(existing);
        }

        // COMPLETED 상태면 기존 시나리오 반환
        return new ScenarioStatusResponse(
                existing.getId(),
                existing.getStatus(),
                "이미 완료된 시나리오가 존재합니다."
        );
    }

    /**
     * 시나리오 생성 트랜잭션 (최소한의 DB 작업만 수행)
     */
    @Transactional
    protected Long createScenarioInTransaction(
            Long userId,
            ScenarioCreateRequest request,
            @Nullable DecisionNodeNextRequest lastDecision,
            DecisionLine decisionLine) {

        try {
            // lastDecision 처리 (필요 시)
            if (lastDecision != null) {
                decisionFlowService.createDecisionNodeNext(lastDecision);
                List<DecisionNode> ordered = decisionNodeRepository.findByDecisionLine_IdOrderByAgeYearAscIdAsc(decisionLine.getId());
                DecisionNode parent = ordered.isEmpty() ? null : ordered.get(ordered.size() - 1);

                // 베이스 라인의 tail BaseNode 해석(“결말” 우선, 없으면 최대 age)
                BaseLine baseLine = decisionLine.getBaseLine();
                List<BaseNode> baseNodes = baseLine.getBaseNodes();
                BaseNode tailBase = baseNodes.stream()
                        .filter(b -> {
                            String s = b.getSituation() == null ? "" : b.getSituation();
                            String d = b.getDecision()  == null ? "" : b.getDecision();
                            return s.contains("결말") || d.contains("결말");
                        })
                        .max(Comparator.comparingInt(BaseNode::getAgeYear).thenComparingLong(BaseNode::getId))
                        .orElseGet(() -> baseNodes.stream()
                                .max(Comparator.comparingInt(BaseNode::getAgeYear).thenComparingLong(BaseNode::getId))
                                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_INPUT_VALUE, "tail base not found"))
                        );

                // 엔티티 빌더로 ‘결말’ 결정노드 저장(테일과 동일 age)
                DecisionNode ending = DecisionNode.builder()
                        .user(decisionLine.getUser())
                        .nodeKind(NodeType.DECISION)
                        .decisionLine(decisionLine)
                        .baseNode(tailBase)
                        .parent(parent)
                        .category(tailBase.getCategory())
                        .situation("결말")
                        .decision("결말")
                        .ageYear(tailBase.getAgeYear())
                        .background(tailBase.getSituation() == null ? "" : tailBase.getSituation())
                        .build();

                decisionNodeRepository.save(ending);
            }

            // DecisionLine 완료 처리
            try {
                decisionLine.complete();
            } catch (RuntimeException e) {
                throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, e.getMessage());
            }

            // 시나리오 생성
            BaseLine baseLine = decisionLine.getBaseLine();
            Scenario scenario = Scenario.builder()
                    .user(decisionLine.getUser())
                    .decisionLine(decisionLine)
                    .baseLine(baseLine)
                    .status(ScenarioStatus.PENDING)
                    .build();

            Scenario savedScenario = scenarioRepository.save(scenario);

            return savedScenario.getId();

        } catch (DataIntegrityViolationException e) {
            // 동시성으로 인한 중복 생성 시 기존 시나리오 ID 반환
            return scenarioRepository.findByDecisionLineId(request.decisionLineId())
                    .map(Scenario::getId)
                    .orElseThrow(() -> new ApiException(ErrorCode.SCENARIO_CREATION_FAILED));
        }
    }

    /**
     * 검증 결과를 담는 내부 클래스
     */
    private record ScenarioValidationResult(
        DecisionLine decisionLine,
        Scenario existingScenario
    ) {}

    // 가장 많이 사용하는 함수 호출 위 한줄 요약: 시나리오 요청에서 lineId 필수 추출
    private Long requireLineId(ScenarioCreateRequest scenario) {
        if (scenario == null || scenario.decisionLineId() == null) {
            throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "scenario.decisionLineId is required");
        }
        return scenario.decisionLineId();
    }

    // 한줄 요약: 소유자/편집 가능 상태 검증
    private void ensureOwnerEditable(Long userId, DecisionLine line) {
        if (!line.getUser().getId().equals(userId))
            throw new ApiException(ErrorCode.HANDLE_ACCESS_DENIED, "not line owner");
        if (line.getStatus() == DecisionLineStatus.COMPLETED || line.getStatus() == DecisionLineStatus.CANCELLED)
            throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "line is not editable");
    }

    // lastDecision의 부모 노드가 같은 라인/같은 사용자/유효한 시퀀스인지 검증
    private void ensureSameLine(DecisionLine line, DecisionNodeNextRequest lastDecision) {
        if (lastDecision.parentDecisionNodeId() == null) {
            throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "lastDecision.parentDecisionNodeId is required");
        }

        // 부모 노드 id로 조회(없으면 404) - DecisionLine과 User를 EAGER 로딩
        DecisionNode parent = decisionNodeRepository.findWithLineAndUserById(lastDecision.parentDecisionNodeId())
                .orElseThrow(() -> new ApiException(
                        ErrorCode.NODE_NOT_FOUND,
                        "parent decision node not found: " + lastDecision.parentDecisionNodeId()
                ));

        // DecisionLine null 체크 (데이터 무결성 검증)
        if (parent.getDecisionLine() == null) {
            throw new ApiException(ErrorCode.INVALID_INPUT_VALUE,
                    "Parent DecisionNode's DecisionLine is missing (data corruption). DecisionNode ID: " + parent.getId());
        }

        // 같은 라인인지 강제
        if (!parent.getDecisionLine().getId().equals(line.getId())) {
            throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "parent node does not belong to the target line");
        }

        // 소유자 일치 강제(추가 방어)
        Long ownerIdOfLine = line.getUser().getId();
        if (!parent.getUser().getId().equals(ownerIdOfLine)) {
            throw new ApiException(ErrorCode.HANDLE_ACCESS_DENIED, "parent node is not owned by line owner");
        }
        if (!parent.getDecisionLine().getUser().getId().equals(ownerIdOfLine)) {
            throw new ApiException(ErrorCode.HANDLE_ACCESS_DENIED, "line owner mismatch on parent node");
        }

        // 시퀀스 유효성: ageYear가 지정됐다면 반드시 부모 이후여야 함
        Integer nextAge = lastDecision.ageYear();
        if (nextAge != null && nextAge <= parent.getAgeYear()) {
            throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "ageYear must be greater than parent.ageYear");
        }

        // 편집 가능 상태 재확인(이미 상위에서 검증했더라도 이중 방어)
        if (line.getStatus() == DecisionLineStatus.COMPLETED || line.getStatus() == DecisionLineStatus.CANCELLED) {
            throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "line is not editable");
        }
    }


    /**
     * FAILED 시나리오 재시도 로직.
     * 트랜잭션과 비동기 처리를 분리하여 커넥션 풀 효율성 향상.
     */
    private ScenarioStatusResponse handleFailedScenarioRetry(Scenario failedScenario) {
        // 1. 상태 업데이트 (트랜잭션)
        Long scenarioId = retryScenarioInTransaction(failedScenario.getId());

        // 2. 비동기 AI 처리 트리거 (트랜잭션 외부, 별도 Bean에서 호출)
        scenarioTransactionService.processScenarioGenerationAsync(scenarioId);

        return new ScenarioStatusResponse(
                scenarioId,
                ScenarioStatus.PENDING,
                "시나리오 재생성이 시작되었습니다."
        );
    }

    /**
     * FAILED 시나리오를 PENDING으로 되돌리는 트랜잭션
     */
    @Transactional
    protected Long retryScenarioInTransaction(Long scenarioId) {
        Scenario scenario = scenarioRepository.findById(scenarioId)
                .orElseThrow(() -> new ApiException(ErrorCode.SCENARIO_NOT_FOUND));

        scenario.setStatus(ScenarioStatus.PENDING);
        scenario.setErrorMessage(null);
        scenario.setUpdatedDate(LocalDateTime.now());

        scenarioRepository.save(scenario);

        return scenario.getId();
    }

    // 시나리오 생성 상태 조회
    @Transactional(readOnly = true)
    public ScenarioStatusResponse getScenarioStatus(Long scenarioId, Long userId) {
        // 권한 검증 및 조회
        Scenario scenario = scenarioRepository.findByIdAndUserIdForStatusCheck(scenarioId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.SCENARIO_NOT_FOUND));

        // DTO 변환 및 반환
        return new ScenarioStatusResponse(
                scenario.getId(),
                scenario.getStatus(),
                getStatusMessage(scenario.getStatus())
        );
    }

    // 시나리오 생성 상태 조회 Helper 메서드 (상태별 메세지)
    private String getStatusMessage(ScenarioStatus status) {
        return switch (status) {
            case PENDING -> "시나리오 생성 대기 중입니다.";
            case PROCESSING -> "시나리오를 생성 중입니다.";
            case COMPLETED -> "시나리오 생성이 완료되었습니다.";
            case FAILED -> "시나리오 생성에 실패했습니다. 다시 시도해주세요.";
        };
    }

    // 시나리오 상세 조회
    @Transactional(readOnly = true)
    public ScenarioDetailResponse getScenarioDetail(Long scenarioId, Long userId) {
        // 권한 검증 및 조회
        Scenario scenario = scenarioRepository.findByIdAndUserId(scenarioId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.SCENARIO_NOT_FOUND));

        // 지표 조회
        var sceneTypes = sceneTypeRepository.findByScenarioIdOrderByTypeAsc(scenarioId);

        // DTO 변환 및 반환
        return ScenarioDetailResponse.from(scenario, sceneTypes);
    }

    // DecisionLine ID로 시나리오 상세 조회
    @Transactional(readOnly = true)
    public ScenarioDetailResponse getScenarioByDecisionLine(Long decisionLineId, Long userId) {
        // DecisionLine에 연결된 시나리오 조회 (권한 검증 포함)
        Scenario scenario = scenarioRepository.findByDecisionLineIdAndUserId(decisionLineId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.SCENARIO_NOT_FOUND,
                        "해당 DecisionLine에 연결된 시나리오를 찾을 수 없습니다."));

        // 지표 조회
        var sceneTypes = sceneTypeRepository.findByScenarioIdOrderByTypeAsc(scenario.getId());

        // DTO 변환 및 반환
        return ScenarioDetailResponse.from(scenario, sceneTypes);
    }

    // 시나리오 타임라인 조회
    @Transactional(readOnly = true)
    public TimelineResponse getScenarioTimeline(Long scenarioId, Long userId) {
        // 권한 검증 및 시나리오 조회
        Scenario scenario = scenarioRepository.findByIdAndUserId(scenarioId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.SCENARIO_NOT_FOUND));

        // 시나리오 상태 확인
        if (scenario.getStatus() != ScenarioStatus.COMPLETED) {
            throw new ApiException(ErrorCode.SCENARIO_NOT_COMPLETED);
        }

        // TimelineTitles JSON 파싱
        Map<String, String> timelineTitles = parseTimelineTitles(scenario.getTimelineTitles());

        // DTO의 static 메서드를 사용하여 변환
        return TimelineResponse.fromTimelineTitlesMap(scenarioId, timelineTitles);
    }


    // JSON 파싱 Helper 메서드
    private Map<String, String> parseTimelineTitles(String timelineTitles) {
        try {
            // Null 이나 빈 문자열 체크
            if (timelineTitles == null || timelineTitles.trim().isEmpty()) {
                throw new ApiException(ErrorCode.SCENARIO_TIMELINE_NOT_FOUND);
            }

            // JSON 문자열을 Map으로 파싱
            return objectMapper.readValue(timelineTitles,
                    new TypeReference<Map<String, String>>() {});
        } catch (JsonProcessingException e) {
            // JSON 파싱 실패 시 예외 처리
            log.error("Failed to parse timeline JSON: {}", e.getMessage());
            throw new ApiException(ErrorCode.SCENARIO_TIMELINE_NOT_FOUND);
        }
    }

    // 시나리오 비교 분석
    @Transactional(readOnly = true)
    public ScenarioCompareResponse compareScenarios(Long baseId, Long compareId, Long userId) {
        // 1. 두 시나리오를 배치 조회 (권한 검증 포함)
        List<Scenario> scenarios = scenarioRepository.findAllById(List.of(baseId, compareId));

        // 존재 여부 및 권한 검증
        if (scenarios.size() != 2) {
            throw new ApiException(ErrorCode.SCENARIO_NOT_FOUND);
        }

        Scenario baseScenario = scenarios.stream()
                .filter(s -> s.getId().equals(baseId))
                .findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.SCENARIO_NOT_FOUND));

        Scenario compareScenario = scenarios.stream()
                .filter(s -> s.getId().equals(compareId))
                .findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.SCENARIO_NOT_FOUND));

        // 권한 검증
        if (!baseScenario.getUser().getId().equals(userId) ||
            !compareScenario.getUser().getId().equals(userId)) {
            throw new ApiException(ErrorCode.HANDLE_ACCESS_DENIED);
        }

        // 2. 두 시나리오의 지표를 배치 조회
        List<SceneType> allSceneTypes = sceneTypeRepository.findByScenarioIdInOrderByScenarioIdAscTypeAsc(
                List.of(baseId, compareId));

        var baseTypes = allSceneTypes.stream()
                .filter(st -> st.getScenario().getId().equals(baseId))
                .toList();

        var compareTypes = allSceneTypes.stream()
                .filter(st -> st.getScenario().getId().equals(compareId))
                .toList();

        // 3. 비교 분석 결과 조회
        List<SceneCompare> compareResults = sceneCompareRepository.findByScenarioIdOrderByResultType(compareId);
        if (compareResults.isEmpty()) {
            throw new ApiException(ErrorCode.SCENE_COMPARE_NOT_FOUND);
        }

        // DTO 변환 및 반환
        return ScenarioCompareResponse.from(
                baseScenario,
                compareScenario,
                compareResults,
                baseTypes,
                compareTypes
        );
    }

    // 베이스라인 목록 조회 (페이지네이션 지원)
    @Transactional(readOnly = true)
    public PageResponse<BaselineListResponse> getBaselines(Long userId, Pageable pageable) {
        // 사용자별 베이스라인 조회 (BaseNode들과 함께 fetch)
        Page<BaseLine> baseLines = baseLineRepository.findAllByUserIdWithBaseNodes(userId, pageable);

        // BaseLine -> BaselineListResponse 변환
        Page<BaselineListResponse> responsePage = baseLines.map(this::convertToBaselineListResponse);

        // PageResponse로 변환 (1-based 페이지네이션)
        return PageResponse.of(responsePage);
    }

    /**
     * BaseLine 엔티티를 BaselineListResponse DTO로 변환
     */
    private BaselineListResponse convertToBaselineListResponse(BaseLine baseLine) {
        // BaseNode들의 카테고리를 태그로 변환 (중복 제거 및 한글명으로 변환)
        List<String> tags = baseLine.getBaseNodes().stream()
                .filter(baseNode -> baseNode.getCategory() != null)
                .map(baseNode -> convertCategoryToKorean(baseNode.getCategory()))
                .distinct()
                .sorted()
                .toList();

        return BaselineListResponse.from(baseLine, tags);
    }

    /**
     * NodeCategory를 한글명으로 변환
     */
    private String convertCategoryToKorean(NodeCategory category) {
        return switch (category) {
            case EDUCATION -> "교육";
            case CAREER -> "진로";
            case RELATIONSHIP -> "관계";
            case FINANCE -> "경제";
            case HEALTH -> "건강";
            case LOCATION -> "거주지";
            case ETC -> "기타";
        };
    }

}