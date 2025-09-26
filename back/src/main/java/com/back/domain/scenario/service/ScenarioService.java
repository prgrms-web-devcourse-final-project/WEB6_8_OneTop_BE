package com.back.domain.scenario.service;

import com.back.domain.node.entity.BaseLine;
import com.back.domain.node.entity.DecisionLine;
import com.back.domain.node.repository.BaseLineRepository;
import com.back.domain.node.repository.DecisionLineRepository;
import com.back.domain.scenario.dto.*;
import com.back.domain.scenario.entity.Scenario;
import com.back.domain.scenario.entity.ScenarioStatus;
import com.back.domain.scenario.entity.SceneCompare;
import com.back.domain.scenario.entity.Type;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

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

        // 시나리오 엔티티 생성 및 저장 (초기 상태는 PENDING)
        Scenario scenario = Scenario.builder()
                .user(decisionLine.getUser())
                .decisionLine(decisionLine)
                .status(ScenarioStatus.PENDING)
                .build();
        Scenario savedScenario = scenarioRepository.save(scenario);

        // 비동기 방식으로 AI 시나리오 생성
        processScenarioGenerationAsync(savedScenario.getId());

        // DTO 변환 및 반환
        return new ScenarioStatusResponse(
                savedScenario.getId(),
                savedScenario.getStatus(),
                "시나리오 생성이 시작되었습니다."
        );
    }

    // 비동기 방식으로 AI 시나리오 생성
    @Async
    @Transactional // TODO: AI 연동시 별도 트랜잭션 관리로 변경 필요
    public void processScenarioGenerationAsync(Long scenarioId) {
        // 시나리오 조회
        Scenario scenario = scenarioRepository.findById(scenarioId)
                .orElseThrow(() -> new ApiException(ErrorCode.SCENARIO_NOT_FOUND));

        try {
            // 상태를 PROCESSING으로 업데이트
            scenario.setStatus(ScenarioStatus.PROCESSING);
            scenarioRepository.save(scenario);

            // AI 시나리오 생성
            aiScenarioGeneration(scenario);

            // 상태를 COMPLETED로 업데이트
            scenario.setStatus(ScenarioStatus.COMPLETED);
            scenarioRepository.save(scenario);
        } catch (Exception e) {
            // 오류 발생 시 상태를 FAILED로 업데이트하고 오류 메시지 저장
            scenario.setStatus(ScenarioStatus.FAILED);
            scenario.setErrorMessage(e.getMessage());
            scenarioRepository.save(scenario);
        }
    }

    // AI 시나리오 생성 (핵심 로직)
    @Transactional
    protected void aiScenarioGeneration(Scenario scenario) {
        try {
            DecisionLine decisionLine = scenario.getDecisionLine();
            BaseLine baseLine = decisionLine.getBaseLine();

            // 1. 베이스 시나리오 확보
            Scenario baseScenario = ensureBaseScenarioExists(baseLine);

            // 2. DecisionScenario 생성 (항상 실행)
            DecisionScenarioResult aiResult = aiService
                    .generateDecisionScenario(decisionLine, baseScenario).join();

            // 3. 결과 적용
            applyDecisionScenarioResult(scenario, aiResult);

            log.info("AI scenario generation completed successfully for Scenario ID: {}", scenario.getId());

        } catch (Exception e) {
            log.error("AI scenario generation failed for Scenario ID: {}, error: {}",
                    scenario.getId(), e.getMessage(), e);
            throw new ApiException(ErrorCode.AI_GENERATION_FAILED, "AI 시나리오 생성 실패: " + e.getMessage());
        }
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
        applyBaseScenarioResult(savedScenario, aiResult);

        return savedScenario;
    }

    // 베이스 시나리오 결과 적용
    @Transactional
    protected void applyBaseScenarioResult(Scenario scenario, BaseScenarioResult aiResult) {
        // 기본 정보 설정
        scenario.setJob(aiResult.job());
        scenario.setTotal(calculateTotalScore(aiResult.indicatorScores()));
        scenario.setSummary(aiResult.summary());
        scenario.setDescription(aiResult.description());

        // 타임라인 처리
        handleTimelineTitles(scenario, aiResult.timelineTitles());

        // 베이스 시나리오는 이미지 없음
        scenario.setImg(null);

        scenarioRepository.save(scenario);

        // 베이스용 SceneType 생성
        createBaseSceneTypes(scenario, aiResult);

        // BaseLine 제목 업데이트
        updateBaseLineTitle(scenario.getBaseLine(), aiResult.baselineTitle());
    }

    // DecisionScenario 결과 적용
    @Transactional
    protected void applyDecisionScenarioResult(Scenario scenario, DecisionScenarioResult aiResult) {
        // 기본 정보 설정
        scenario.setJob(aiResult.job());
        scenario.setTotal(calculateTotalScore(aiResult.indicatorScores()));
        scenario.setSummary(aiResult.summary());
        scenario.setDescription(aiResult.description());

        // 타임라인 처리
        handleTimelineTitles(scenario, aiResult.timelineTitles());

        // 이미지 생성 처리
        handleImageGeneration(scenario, aiResult.imagePrompt());

        scenarioRepository.save(scenario);

        // 지표별 SceneType 생성
        createDecisionSceneTypes(scenario, aiResult);

        // 비교 분석 결과 생성
        createSceneCompare(scenario, aiResult);
    }

    private void handleTimelineTitles(Scenario scenario, Map<String, String> timelineTitles) {
        try {
            String timelineTitlesJson = objectMapper.writeValueAsString(timelineTitles);
            scenario.setTimelineTitles(timelineTitlesJson);
        } catch (Exception e) {
            log.error("Failed to serialize timeline titles for scenario {}: {}",
                    scenario.getId(), e.getMessage());
            scenario.setTimelineTitles("{}");
        }
    }

    private void handleImageGeneration(Scenario scenario, String imagePrompt) {
        try {
            if (imagePrompt != null && !imagePrompt.trim().isEmpty()) {
                String imageUrl = aiService.generateImage(imagePrompt).join();

                if ("placeholder-image-url".equals(imageUrl) || imageUrl == null || imageUrl.trim().isEmpty()) {
                    scenario.setImg(null);
                    log.info("Image generation not available for scenario {}, stored as null", scenario.getId());
                } else {
                    scenario.setImg(imageUrl);
                    log.info("Image generated successfully for scenario {}", scenario.getId());
                }
            } else {
                scenario.setImg(null);
            }
        } catch (Exception e) {
            scenario.setImg(null);
            log.warn("Image generation failed for scenario {}: {}. Continuing without image.",
                    scenario.getId(), e.getMessage());
        }
    }

    private int calculateTotalScore(Map<Type, Integer> indicatorScores) {
        return indicatorScores.values().stream()
                .mapToInt(Integer::intValue)
                .sum();
    }

    private void createBaseSceneTypes(Scenario scenario, BaseScenarioResult aiResult) {
        List<com.back.domain.scenario.entity.SceneType> sceneTypes = aiResult.indicatorScores()
                .entrySet().stream()
                .map(entry -> {
                    Type type = entry.getKey();
                    int point = entry.getValue();
                    String analysis = aiResult.indicatorAnalysis().get(type);

                    return com.back.domain.scenario.entity.SceneType.builder()
                            .scenario(scenario)
                            .type(type)
                            .point(point) // AI가 분석한 실제 점수
                            .analysis(analysis != null ? analysis : "현재 " + type.name() + " 상황 분석")
                            .build();
                })
                .toList();

        sceneTypeRepository.saveAll(sceneTypes);
    }

    private void createDecisionSceneTypes(Scenario scenario, DecisionScenarioResult aiResult) {
        List<com.back.domain.scenario.entity.SceneType> sceneTypes = aiResult.indicatorScores()
                .entrySet().stream()
                .map(entry -> {
                    Type type = entry.getKey();
                    int point = entry.getValue();
                    String analysis = aiResult.indicatorAnalysis().get(type);

                    return com.back.domain.scenario.entity.SceneType.builder()
                            .scenario(scenario)
                            .type(type)
                            .point(point)
                            .analysis(analysis != null ? analysis : "분석 정보를 가져올 수 없습니다.")
                            .build();
                })
                .toList();

        sceneTypeRepository.saveAll(sceneTypes);
    }

    private void createSceneCompare(Scenario scenario, DecisionScenarioResult aiResult) {
        List<SceneCompare> compares = aiResult.comparisonResults()
                .entrySet().stream()
                .map(entry -> {
                    String resultTypeStr = entry.getKey().toUpperCase();
                    com.back.domain.scenario.entity.SceneCompareResultType resultType;
                    try {
                        resultType = com.back.domain.scenario.entity.SceneCompareResultType.valueOf(resultTypeStr);
                    } catch (IllegalArgumentException e) {
                        log.warn("Unknown result type: {}, using TOTAL as fallback", resultTypeStr);
                        resultType = com.back.domain.scenario.entity.SceneCompareResultType.TOTAL;
                    }

                    return SceneCompare.builder()
                            .scenario(scenario)
                            .resultType(resultType)
                            .compareResult(entry.getValue())
                            .build();
                })
                .toList();

        sceneCompareRepository.saveAll(compares);
    }

    private void updateBaseLineTitle(BaseLine baseLine, String baselineTitle) {
        if (baselineTitle != null && !baselineTitle.trim().isEmpty()) {
            baseLine.setTitle(baselineTitle);
            baseLineRepository.save(baseLine);
            log.info("BaseLine title updated for ID: {}", baseLine.getId());
        }
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

        // DecisionLine 조회 -> DecisionNodes 추출 (DecisionLine 미구현으로 임시)
        // TODO: DecisionLine 및 DecisionNode 구현 후 교체
        List<MockDecisionNode> mockNodes = createMockDecisionNodes();

        // TimelineTitles JSON 파싱
        Map<String, String> timelineTitles = parseTimelineTitles(scenario.getTimelineTitles());

        // TimelineEvent 리스트 생성
        List<TimelineResponse.TimelineEvent> events = mockNodes.stream()
                .map(node -> new TimelineResponse.TimelineEvent(
                        node.year,
                        timelineTitles.getOrDefault(String.valueOf(node.year), "선택 결과")
                ))
                .sorted(Comparator.comparing(TimelineResponse.TimelineEvent::year))
                .toList();

        // DTO 변환 및 반환
        return new TimelineResponse(scenarioId, events);
    }

    // 시나리오 타임라인 조회 Helper
    // Mock DecisionNode 클래스 (추후 실제 엔티티로 교체)
    private record MockDecisionNode(int year, String title) {}

    // Mock 데이터 생성 메서드 (추후 실제 데이터로 교체)
    private List<MockDecisionNode> createMockDecisionNodes() {
        return List.of(
                new MockDecisionNode(2020, "창업 도전"),
                new MockDecisionNode(2022, "해외 진출"),
                new MockDecisionNode(2025, "상장 성공")
        );
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

    // 베이스라인 목록 조회
    @Transactional(readOnly = true)
    public List<BaselineListResponse> getBaselines(Long userId) {
        // TODO: 실제 구현 시 BaseLineRepository.findAllByUserId(userId) 사용
        // 현재는 Mock 데이터로 MVP 완성

        // Mock 베이스라인 데이터 생성
        return List.of(
                new BaselineListResponse(
                        1001L,
                        "대학 졸업 후 진로 선택",
                        List.of("교육", "진로", "취업"),
                        LocalDateTime.of(2024, 1, 15, 10, 30)
                ),
                new BaselineListResponse(
                        1002L,
                        "회사 이직 후 새 시작",
                        List.of("커리어", "성장", "도전"),
                        LocalDateTime.of(2024, 3, 22, 14, 45)
                ),
                new BaselineListResponse(
                        1003L,
                        "결혼 후 인생 설계",
                        List.of("가족", "관계", "안정"),
                        LocalDateTime.of(2024, 6, 10, 16, 20)
                )
        );
    }
}