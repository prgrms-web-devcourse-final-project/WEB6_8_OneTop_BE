package com.back.domain.scenario.service;

import com.back.domain.node.entity.BaseLine;
import com.back.domain.node.repository.BaseLineRepository;
import com.back.domain.scenario.dto.AiScenarioGenerationResult;
import com.back.domain.scenario.entity.*;
import com.back.domain.scenario.repository.ScenarioRepository;
import com.back.domain.scenario.repository.SceneCompareRepository;
import com.back.domain.scenario.repository.SceneTypeRepository;
import com.back.global.ai.dto.result.BaseScenarioResult;
import com.back.global.ai.dto.result.DecisionScenarioResult;
import com.back.global.ai.exception.AiParsingException;
import com.back.global.ai.service.AiService;
import com.back.global.exception.ApiException;
import com.back.global.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 시나리오 관련 트랜잭션 전용 서비스.
 * AI 호출과 DB 작업 분리로 Long Running Transaction 방지
 * ScenarioService의 Self-Invocation 문제 해결
 * REQUIRES_NEW로 독립적 트랜잭션 보장
 */

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class ScenarioTransactionService {


    private final ScenarioRepository scenarioRepository;
    private final SceneCompareRepository sceneCompareRepository;
    private final SceneTypeRepository sceneTypeRepository;
    private final BaseLineRepository baseLineRepository;
    private final AiService aiService;
    private final ObjectMapper objectMapper;
    private final com.back.global.ai.config.ImageAiConfig imageAiConfig;

    // 상태 업데이트 전용 트랜잭션 메서드
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateScenarioStatus(Long scenarioId, ScenarioStatus status, String errorMessage) {
        Scenario scenario = scenarioRepository.findById(scenarioId)
                .orElseThrow(() -> new ApiException(ErrorCode.SCENARIO_NOT_FOUND));

        scenario.setStatus(status);
        scenario.setUpdatedDate(LocalDateTime.now());

        if (errorMessage != null) {
            scenario.setErrorMessage(errorMessage);
        }

        scenarioRepository.save(scenario);
    }

    // AI 결과 저장 전용 트랜잭션 메서드
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveAiResult(Long scenarioId, AiScenarioGenerationResult result) {
        Scenario scenario = scenarioRepository.findById(scenarioId)
                .orElseThrow(() -> new ApiException(ErrorCode.SCENARIO_NOT_FOUND));

        // AI 결과 적용
        if (result.isBaseScenario()) {
            applyBaseScenarioResult(scenario, result.getBaseResult());
        } else {
            applyDecisionScenarioResult(scenario, result.getDecisionResult());
        }
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
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize timeline titles for scenario {}: {}",
                    scenario.getId(), e.getMessage());
            scenario.setTimelineTitles("{}");
        }
    }

    private void handleImageGeneration(Scenario scenario, String imagePrompt) {
        try {
            if (imagePrompt != null && !imagePrompt.trim().isEmpty()) {
                String imageUrl = aiService.generateImage(imagePrompt)
                        .orTimeout(imageAiConfig.getTimeoutSeconds(), java.util.concurrent.TimeUnit.SECONDS)
                        .exceptionally(ex -> {
                            log.warn("Image generation timeout or error for scenario {}: {}",
                                    scenario.getId(), ex.getMessage());
                            return null;
                        })
                        .join();

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
        if (indicatorScores == null || indicatorScores.isEmpty()) {
            log.warn("indicatorScores is null or empty, returning 0");
            return 0;
        }
        return indicatorScores.values().stream()
                .mapToInt(Integer::intValue)
                .sum();
    }

    private void createBaseSceneTypes(Scenario scenario, BaseScenarioResult aiResult) {
        if (aiResult.indicators() == null || aiResult.indicators().isEmpty()) {
            log.warn("No indicator scores available for scenario {}", scenario.getId());
            return;
        }

        List<SceneType> sceneTypes = aiResult.indicators().stream()
                .map(indicator -> {
                    Type type = Type.valueOf(indicator.type());

                    return com.back.domain.scenario.entity.SceneType.builder()
                            .scenario(scenario)
                            .type(type)
                            .point(indicator.point()) // AI가 분석한 실제 점수
                            .analysis(indicator.analysis() != null ? indicator.analysis() : "현재 " + type.name() + " 상황 분석")
                            .build();
                })
                .toList();

        sceneTypeRepository.saveAll(sceneTypes);
    }

    private void createDecisionSceneTypes(Scenario scenario, DecisionScenarioResult aiResult) {
        if (aiResult.indicators() == null || aiResult.indicators().isEmpty()) {
            throw new AiParsingException("Indicator scores are missing in AI response for scenario " + scenario.getId());
        }

        List<com.back.domain.scenario.entity.SceneType> sceneTypes = aiResult.indicators().stream()
                .map(indicator -> {
                    Type type = Type.valueOf(indicator.type());

                    return com.back.domain.scenario.entity.SceneType.builder()
                            .scenario(scenario)
                            .type(type)
                            .point(indicator.point())
                            .analysis(indicator.analysis() != null ? indicator.analysis() : "분석 정보를 가져올 수 없습니다.")
                            .build();
                })
                .toList();

        sceneTypeRepository.saveAll(sceneTypes);
    }

    private void createSceneCompare(Scenario scenario, DecisionScenarioResult aiResult) {
        if (aiResult.comparisons() == null || aiResult.comparisons().isEmpty()) {
            throw new AiParsingException("Comparison results are missing in AI response for scenario " + scenario.getId());
        }
        List<SceneCompare> compares = aiResult.comparisons().stream()
                .map(comparison -> {
                    String resultTypeStr = comparison.type().toUpperCase();
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
                            .compareResult(comparison.analysis())
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

    @Transactional(readOnly = true)
    public Scenario prepareScenarioData(Long scenarioId) {
        // MultipleBagFetchException을 피하기 위해 별도 쿼리로 각 컬렉션을 초기화
        // 1. DecisionNodes 초기화
        Scenario scenario = scenarioRepository.findById(scenarioId)
                .orElseThrow(() -> new ApiException(ErrorCode.SCENARIO_NOT_FOUND));
        scenario.getDecisionLine().getDecisionNodes().size(); // 프록시 초기화

        // 2. BaseNodes 초기화
        scenario.getBaseLine().getBaseNodes().size(); // 프록시 초기화

        // 3. User 엔티티 초기화 (새로운 오류 방지)
        scenario.getUser().getMbti(); // User 프록시 초기화

        return scenario;
    }
}
