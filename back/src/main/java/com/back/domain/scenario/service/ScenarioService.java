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
import com.back.domain.user.entity.User;
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
import java.util.HashMap;
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

    // AI 시나리오 생성
    @Transactional
    protected void aiScenarioGeneration(Scenario scenario) {
        try {
            DecisionLine decisionLine = scenario.getDecisionLine();
            BaseLine baseLine = decisionLine.getBaseLine();

            // BaseScenario 존재 확인 및 생성
            Scenario baseScenario = ensureBaseScenarioExists(baseLine);

            // DecisionScenario 생성
            DecisionScenarioResult aiResult = aiService
                    .generateDecisionScenario(decisionLine, baseScenario).get();
            applyDecisionScenarioResult(scenario, aiResult);

            log.info("AI scenario generation completed successfully for Scenario ID: {}", scenario.getId());

        } catch (Exception e) {
            log.error("AI scenario generation failed for Scenario ID: {}, error: {}",
                    scenario.getId(), e.getMessage(), e);
            throw new ApiException(ErrorCode.AI_GENERATION_FAILED, "AI 시나리오 생성 실패: " + e.getMessage());
        }
    }

    private Scenario ensureBaseScenarioExists(BaseLine baseLine) {
        return scenarioRepository.findFirstByDecisionLine_BaseLineIdOrderByCreatedDateAsc(baseLine.getId())
                .orElseGet(() -> createBaseScenario(baseLine));
    }

    private Scenario createBaseScenario(BaseLine baseLine) {

        BaseScenarioResult aiResult = aiService.generateBaseScenario(baseLine).get();

        Scenario baseScenario = Scenario.builder()
                .user(baseLine.getUser())
                .decisionLine(null) // 베이스는 DecisionLine 없음
                .status(ScenarioStatus.COMPLETED) // 베이스는 바로 완료 처리
                .build();

        Scenario savedScenario = scenarioRepository.save(baseScenario);

        applyBaseScenarioResult(savedScenario, aiResult);

        return savedScenario;
    }

    @Transactional
    protected void applyBaseScenarioResult(Scenario scenario, BaseScenarioResult aiResult) {

        scenario.setJob(aiResult.job());
        scenario.setTotal(calculateBaseScore(aiResult)); // 베이스용 점수 계산
        scenario.setSummary(aiResult.summary());
        scenario.setDescription(aiResult.description());

        handleBaseTimelineTitles(scenario, aiResult.timelineTitles());

        // 이미지는 베이스 시나리오에서 제외
        scenario.setImg(null);

        scenarioRepository.save(scenario);

        // 베이스용 SceneType 생성
        createSceneTypesFromBase(scenario, aiResult);

        // BaseLine 제목 업데이트
        updateBaseLineTitle(scenario.getUser(), aiResult.baselineTitle());
    }

    private int calculateBaseScore(BaseScenarioResult aiResult) {
        // 베이스는 모든 지표 50점씩 = 250점
        return 250;
    }

    private void handleBaseTimelineTitles(Scenario scenario, List<String> timelineTitles) {
        try {
            Map<String, String> timelineMap = convertTimelineTitlesToMap(timelineTitles);
            String timelineTitlesJson = objectMapper.writeValueAsString(timelineMap);
            scenario.setTimelineTitles(timelineTitlesJson);
        } catch (Exception e) {
            log.error("Failed to serialize base timeline titles: {}", e.getMessage());
            scenario.setTimelineTitles("{}");
        }
    }

    private void createSceneTypesFromBase(Scenario scenario, BaseScenarioResult aiResult) {
        // 베이스는 모든 지표 50점으로 생성
        List<com.back.domain.scenario.entity.SceneType> sceneTypes = List.of(
                        com.back.domain.scenario.entity.Type.경제,
                        com.back.domain.scenario.entity.Type.행복,
                        com.back.domain.scenario.entity.Type.관계,
                        com.back.domain.scenario.entity.Type.직업,
                        com.back.domain.scenario.entity.Type.건강
                ).stream()
                .map(type -> com.back.domain.scenario.entity.SceneType.builder()
                        .scenario(scenario)
                        .type(type)
                        .point(50) // 베이스는 모두 50점
                        .analysis("현재 " + type.name() + " 상황 기준점")
                        .build())
                .toList();

        sceneTypeRepository.saveAll(sceneTypes);
    }

    private void updateBaseLineTitle(User user, String baselineTitle) {
        // BaseLine 제목 업데이트 로직
        // baseLineRepository.updateTitleByUserId(user.getId(), baselineTitle);
    }

    @Transactional
    protected void applyDecisionScenarioResult(Scenario scenario, DecisionScenarioResult aiResult) {

        scenario.setJob(aiResult.job());
        scenario.setTotal(calculateTotalScore(aiResult.indicatorScores()));
        scenario.setSummary(aiResult.summary());
        scenario.setDescription(aiResult.description());

        handleTimelineTitles(scenario, aiResult.timelineTitles());
        handleImageGeneration(scenario, aiResult.imagePrompt());

        scenarioRepository.save(scenario);

        createSceneTypes(scenario, aiResult);
        createSceneCompare(scenario, aiResult);
    }

    private int calculateTotalScore(Map<Type, Integer> indicatorScores) {
        return indicatorScores.values().stream()
                .mapToInt(Integer::intValue)
                .sum();
    }

    private void handleTimelineTitles(Scenario scenario, List<String> timelineTitles) {
        try {
            Map<String, String> timelineMap = convertTimelineTitlesToMap(timelineTitles);
            String timelineTitlesJson = objectMapper.writeValueAsString(timelineMap);
            scenario.setTimelineTitles(timelineTitlesJson);
        } catch (Exception e) {
            log.error("Failed to serialize timeline titles for scenario {}: {}",
                    scenario.getId(), e.getMessage());
            scenario.setTimelineTitles("{}");
        }
    }

    private Map<String, String> convertTimelineTitlesToMap(List<String> timelineTitles) {
        // AI 결과 구조에 따라 구현 조정 필요
        // 예시: 3개 제목을 2020, 2022, 2025년으로 매핑
        Map<String, String> result = new HashMap<>();
        if (timelineTitles != null && !timelineTitles.isEmpty()) {
            for (int i = 0; i < timelineTitles.size() && i < 3; i++) {
                int year = 2020 + (i * 2); // 2년 간격
                result.put(String.valueOf(year), timelineTitles.get(i));
            }
        }
        return result;
    }

    private void handleImageGeneration(Scenario scenario, String imagePrompt) {
        try {
            if (imagePrompt != null && !imagePrompt.trim().isEmpty()) {
                log.debug("Attempting image generation for scenario {} with prompt: {}",
                        scenario.getId(), imagePrompt.substring(0, Math.min(50, imagePrompt.length())));

                // 이미지 생성 시도 (현재는 placeholder 반환)
                String imageUrl = aiService.generateImage(imagePrompt).get();

                // Placeholder이거나 null이면 → null로 저장
                if ("placeholder-image-url".equals(imageUrl) || imageUrl == null || imageUrl.trim().isEmpty()) {
                    scenario.setImg(null);
                    log.info("Image generation not available for scenario {}, stored as null", scenario.getId());
                } else {
                    // 실제 이미지 URL 저장
                    scenario.setImg(imageUrl);
                    log.info("Image generated successfully for scenario {}", scenario.getId());
                }
            } else {
                // 이미지 프롬프트가 없으면 null
                scenario.setImg(null);
                log.debug("No image prompt provided for scenario {}", scenario.getId());
            }
        } catch (Exception e) {
            // 이미지 생성 실패 → null로 저장하고 경고 로그만 남김
            scenario.setImg(null);
            log.warn("Image generation failed for scenario {}: {}. Continuing without image.",
                    scenario.getId(), e.getMessage());
            // 예외를 다시 던지지 않음 - 시나리오 생성은 계속
        }
    }

    protected void createSceneTypes(Scenario scenario, DecisionScenarioResult aiResult) {
        try {
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
            log.debug("Created {} SceneTypes for scenario {}", sceneTypes.size(), scenario.getId());
        } catch (Exception e) {
            log.error("Failed to create SceneTypes for scenario {}: {}", scenario.getId(), e.getMessage());
            throw e; // SceneType 생성 실패는 전체 실패로 처리
        }
    }

    protected void createSceneCompare(Scenario scenario, DecisionScenarioResult aiResult) {
        try {
            List<SceneCompare> compares = aiResult.comparisonResults()
                    .entrySet().stream()
                    .map(entry -> {
                        String resultTypeStr = entry.getKey().toUpperCase();
                        // TOTAL이 있는지 확인하고 없으면 기본값 사용
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
            log.debug("Created {} SceneCompare entries for scenario {}", compares.size(), scenario.getId());
        } catch (Exception e) {
            log.error("Failed to create SceneCompare for scenario {}: {}", scenario.getId(), e.getMessage());
            throw e; // 비교 결과 생성 실패는 전체 실패로 처리
        }
    }

    // 시나리오 생성 상태 조회
    @Transactional(readOnly = true) // TODO: readonly 쓰는 이유 공부하기
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