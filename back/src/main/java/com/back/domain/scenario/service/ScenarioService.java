package com.back.domain.scenario.service;

import com.back.domain.node.entity.BaseLine;
import com.back.domain.node.entity.DecisionLine;
import com.back.domain.node.entity.NodeCategory;
import com.back.domain.node.repository.BaseLineRepository;
import com.back.domain.node.repository.DecisionLineRepository;
import com.back.domain.scenario.dto.*;
import com.back.domain.scenario.entity.Scenario;
import com.back.domain.scenario.entity.ScenarioStatus;
import com.back.domain.scenario.entity.SceneCompare;
import com.back.domain.scenario.repository.ScenarioRepository;
import com.back.domain.scenario.repository.SceneCompareRepository;
import com.back.domain.scenario.repository.SceneTypeRepository;
import com.back.global.ai.dto.result.BaseScenarioResult;
import com.back.global.ai.dto.result.DecisionScenarioResult;
import com.back.global.ai.service.AiService;
import com.back.global.exception.ApiException;
import com.back.global.exception.ErrorCode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
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

    // Object Mapper 주입
    private final ObjectMapper objectMapper;

    // AI Service 주입
    private final AiService aiService;

    // Scenario Transaction Service 주입
    private final ScenarioTransactionService scenarioTransactionService;

    // 시나리오 생성
    @Transactional
    public ScenarioStatusResponse createScenario(Long userId, ScenarioCreateRequest request) {
        // DecisionLine 존재 여부 확인
        DecisionLine decisionLine = decisionLineRepository.findById(request.decisionLineId())
                .orElseThrow(() -> new ApiException(ErrorCode.DECISION_LINE_NOT_FOUND));

        // 권한 검증 (DecisionLine 소유자와 요청자 일치 여부)
        if (!decisionLine.getUser().getId().equals(userId)) {
            throw new ApiException(ErrorCode.HANDLE_ACCESS_DENIED);
        }

        // 원자적 조회 + 상태 확인
        Optional<Scenario> existingScenario = scenarioRepository
                .findByDecisionLineId(request.decisionLineId());

        if (existingScenario.isPresent()) {
            Scenario existing = existingScenario.get();

            // PENDING/PROCESSING 상태면 중복 생성 방지
            if (existing.getStatus() == ScenarioStatus.PENDING ||
                    existing.getStatus() == ScenarioStatus.PROCESSING) {
                throw new ApiException(ErrorCode.SCENARIO_ALREADY_IN_PROGRESS,
                        "해당 선택 경로의 시나리오가 이미 생성 중입니다.");
            }

            // FAILED 상태면 재시도 로직
            if (existing.getStatus() == ScenarioStatus.FAILED) {
                return handleFailedScenarioRetry(existing);
            }

            // COMPLETED 상태면 기존 시나리오 반환
            if (existing.getStatus() == ScenarioStatus.COMPLETED) {
                return new ScenarioStatusResponse(
                        existing.getId(),
                        existing.getStatus(),
                        "이미 완료된 시나리오가 존재합니다."
                );
            }
        }

        // 새 시나리오 생성 (DataIntegrityViolationException 처리)
        try {
            Scenario scenario = Scenario.builder()
                    .user(decisionLine.getUser())
                    .decisionLine(decisionLine)
                    .status(ScenarioStatus.PENDING)
                    .build();

            Scenario savedScenario = scenarioRepository.save(scenario);
            processScenarioGenerationAsync(savedScenario.getId());

            return new ScenarioStatusResponse(
                    savedScenario.getId(),
                    savedScenario.getStatus(),
                    "시나리오 생성이 시작되었습니다."
            );

        } catch (DataIntegrityViolationException e) {
            // 동시성으로 인한 중복 생성 시 기존 시나리오 조회 후 반환
            return scenarioRepository.findByDecisionLineId(request.decisionLineId())
                    .map(existing -> new ScenarioStatusResponse(
                            existing.getId(),
                            existing.getStatus(),
                            "기존 시나리오를 반환합니다."
                    ))
                    .orElseThrow(() -> new ApiException(ErrorCode.SCENARIO_CREATION_FAILED));
        }
    }

    // FAILED 시나리오 재시도 로직 분리
    private ScenarioStatusResponse handleFailedScenarioRetry(Scenario failedScenario) {
        failedScenario.setStatus(ScenarioStatus.PENDING);
        failedScenario.setErrorMessage(null);
        failedScenario.setUpdatedDate(LocalDateTime.now());

        Scenario savedScenario = scenarioRepository.save(failedScenario);
        processScenarioGenerationAsync(savedScenario.getId());

        return new ScenarioStatusResponse(
                savedScenario.getId(),
                savedScenario.getStatus(),
                "시나리오 재생성이 시작되었습니다."
        );
    }

    // 비동기 방식으로 AI 시나리오 생성
    @Async
    public void processScenarioGenerationAsync(Long scenarioId) {
        try {
            // 1. 상태를 PROCESSING으로 업데이트 (별도 트랜잭션)
            scenarioTransactionService.updateScenarioStatus(scenarioId, ScenarioStatus.PROCESSING, null);

            // 2. AI 시나리오 생성 (트랜잭션 외부에서 실행)
            AiScenarioGenerationResult result = executeAiGeneration(scenarioId);

            // 3. 결과 저장 및 완료 상태 업데이트 (별도 트랜잭션)
            scenarioTransactionService.saveAiResult(scenarioId, result);
            scenarioTransactionService.updateScenarioStatus(scenarioId, ScenarioStatus.COMPLETED, null);

            log.info("Scenario generation completed successfully for ID: {}", scenarioId);

        } catch (Exception e) {
            // 4. 실패 상태 업데이트 (별도 트랜잭션)
            scenarioTransactionService.updateScenarioStatus(scenarioId, ScenarioStatus.FAILED,
                    "시나리오 생성 실패: " + e.getMessage());
            log.error("Scenario generation failed for ID: {}, error: {}",
                    scenarioId, e.getMessage(), e);
        }
    }

    // AI 호출 전용 메서드 (트랜잭션 없음)
    private AiScenarioGenerationResult executeAiGeneration(Long scenarioId) {
        Scenario scenario = scenarioRepository.findById(scenarioId)
                .orElseThrow(() -> new ApiException(ErrorCode.SCENARIO_NOT_FOUND));

        // AI 호출 로직 (트랜잭션 외부에서 실행)
        DecisionLine decisionLine = scenario.getDecisionLine();
        BaseLine baseLine = decisionLine.getBaseLine();

        // 베이스 시나리오 확보
        Scenario baseScenario = ensureBaseScenarioExists(baseLine);

        // AI 호출 (트랜잭션 외부)
        DecisionScenarioResult aiResult = aiService
                .generateDecisionScenario(decisionLine, baseScenario).join();

        return new AiScenarioGenerationResult(aiResult);
    }

    // 베이스 시나리오 확보 (없으면 생성)
    private Scenario ensureBaseScenarioExists(BaseLine baseLine) {
        return scenarioRepository.findByBaseLineIdAndDecisionLineIsNull(baseLine.getId())
                .orElseGet(() -> createBaseScenario(baseLine));
    }

    // 베이스 시나리오 생성
    private Scenario createBaseScenario(BaseLine baseLine) {
        log.info("Creating base scenario for BaseLine ID: {}", baseLine.getId());

        // 1. AI 호출
        BaseScenarioResult aiResult = aiService.generateBaseScenario(baseLine).join();

        // 2. 베이스 시나리오 엔티티 생성
        Scenario baseScenario = Scenario.builder()
                .user(baseLine.getUser())
                .decisionLine(null) // 베이스 시나리오는 DecisionLine 없음
                .baseLine(baseLine) // 베이스 시나리오는 BaseLine 연결
                .status(ScenarioStatus.COMPLETED) // 베이스는 바로 완료
                .build();

        Scenario savedScenario = scenarioRepository.save(baseScenario);

        // 3. AI 결과 적용
        scenarioTransactionService.applyBaseScenarioResult(savedScenario, aiResult);

        return savedScenario;
    }

    // 시나리오 생성 상태 조회
    @Transactional(readOnly = true)
    public ScenarioStatusResponse getScenarioStatus(Long scenarioId, Long userId) {
        // 권한 검증 및 조회
        Scenario scenario = scenarioRepository.findByIdAndUserId(scenarioId, userId)
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
        return new ScenarioDetailResponse(
                scenario.getId(),
                scenario.getStatus(),
                scenario.getJob(),
                scenario.getTotal(),
                scenario.getSummary(),
                scenario.getDescription(),
                scenario.getImg(),
                scenario.getCreatedDate(),
                sceneTypes.stream()
                        .map(st -> new ScenarioTypeDto(st.getType(), st.getPoint(), st.getAnalysis()))
                        .toList()
        );
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

        // TimelineEvent 리스트 생성 (연도를 key로, 타이틀을 value로 저장된 데이터 활용)
        List<TimelineResponse.TimelineEvent> events = timelineTitles.entrySet().stream()
                .map(entry -> {
                    try {
                        int year = Integer.parseInt(entry.getKey());
                        return new TimelineResponse.TimelineEvent(year, entry.getValue());
                    } catch (NumberFormatException e) {
                        // 연도가 숫자가 아닌 경우 무시
                        return null;
                    }
                })
                .filter(event -> event != null)
                .sorted(Comparator.comparing(TimelineResponse.TimelineEvent::year))
                .toList();

        // DTO 변환 및 반환
        return new TimelineResponse(scenarioId, events);
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
        } catch (Exception e) {
            // JSON 파싱 실패 시 예외 처리
            throw new ApiException(ErrorCode.SCENARIO_TIMELINE_NOT_FOUND);
        }
    }

    // 시나리오 비교 분석
    @Transactional(readOnly = true)
    public ScenarioCompareResponse compareScenarios(Long baseId, Long compareId, Long userId) {
        // 권한 검증 및 시나리오 조회
        Scenario baseScenario = scenarioRepository.findByIdAndUserId(baseId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.SCENARIO_NOT_FOUND));
        Scenario compareScenario = scenarioRepository.findByIdAndUserId(compareId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.SCENARIO_NOT_FOUND));

        // 지표 조회
        var baseTypes = sceneTypeRepository.findByScenarioIdOrderByTypeAsc(baseId);
        var compareTypes = sceneTypeRepository.findByScenarioIdOrderByTypeAsc(compareId);

        // 비교 분석 결과 조회
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
    public Page<BaselineListResponse> getBaselines(Long userId, Pageable pageable) {
        // 사용자별 베이스라인 조회 (BaseNode들과 함께 fetch)
        Page<BaseLine> baseLines = baseLineRepository.findAllByUserIdWithBaseNodes(userId, pageable);

        // BaseLine -> BaselineListResponse 변환
        return baseLines.map(this::convertToBaselineListResponse);
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

        return new BaselineListResponse(
                baseLine.getId(),
                baseLine.getTitle() != null ? baseLine.getTitle() : "제목 없음",
                tags,
                baseLine.getCreatedDate()
        );
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

    // AI 생성 결과를 담는 래퍼 클래스 (트랜잭션 분리용)
    @Getter
    static class AiScenarioGenerationResult {
        private final boolean isBaseScenario;
        private final BaseScenarioResult baseResult;
        private final DecisionScenarioResult decisionResult;

        // 베이스 시나리오용 생성자
        public AiScenarioGenerationResult(BaseScenarioResult baseResult) {
            this.isBaseScenario = true;
            this.baseResult = baseResult;
            this.decisionResult = null;
        }

        // 결정 시나리오용 생성자
        public AiScenarioGenerationResult(DecisionScenarioResult decisionResult) {
            this.isBaseScenario = false;
            this.baseResult = null;
            this.decisionResult = decisionResult;
        }

    }
}