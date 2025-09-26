package com.back.global.ai.service;

import com.back.domain.node.entity.BaseLine;
import com.back.domain.node.entity.DecisionLine;
import com.back.domain.node.entity.DecisionNode;
import com.back.domain.scenario.entity.Scenario;
import com.back.domain.scenario.entity.SceneType;
import com.back.domain.scenario.repository.SceneTypeRepository;
import com.back.global.ai.client.text.TextAiClient;
import com.back.global.ai.dto.result.BaseScenarioResult;
import com.back.global.ai.dto.result.DecisionScenarioResult;
import com.back.global.ai.exception.AiParsingException;
import com.back.global.ai.prompt.BaseScenarioPrompt;
import com.back.global.ai.prompt.DecisionScenarioPrompt;
import com.back.global.ai.prompt.SituationPrompt;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiServiceImpl implements AiService {

    private final TextAiClient textAiClient;
    private final ObjectMapper objectMapper;
    private final SceneTypeRepository sceneTypeRepository;

    @Override
    public CompletableFuture<BaseScenarioResult> generateBaseScenario(BaseLine baseLine) {
        log.info("Generating base scenario for BaseLine ID: {}", baseLine.getId());

        if (baseLine == null) {
            return CompletableFuture.failedFuture(
                    new AiParsingException("BaseLine cannot be null"));
        }

        try {
            // Step 1: 프롬프트 생성
            String baseScenarioPrompt = BaseScenarioPrompt.generatePrompt(baseLine);
            log.debug("Generated base scenario prompt for BaseLine ID: {}", baseLine.getId());

            // Step 2: AI 호출 및 파싱
            return textAiClient.generateText(baseScenarioPrompt)
                    .thenApply(aiResponse -> {
                        try {
                            log.debug("Received AI response for BaseLine ID: {}, length: {}",
                                    baseLine.getId(), aiResponse.length());
                            return objectMapper.readValue(aiResponse, BaseScenarioResult.class);
                        } catch (Exception e) {
                            log.error("Failed to parse BaseScenario AI response for BaseLine ID: {}, error: {}",
                                    baseLine.getId(), e.getMessage(), e);
                            throw new AiParsingException("Failed to parse BaseScenario response: " + e.getMessage());
                        }
                    });
        } catch (Exception e) {
            log.error("Error in generateBaseScenario for BaseLine ID: {}, error: {}",
                    baseLine.getId(), e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public CompletableFuture<DecisionScenarioResult> generateDecisionScenario(DecisionLine decisionLine, Scenario baseScenario) {
        log.info("Generating Decision scenario for DecisionLine ID: {}", decisionLine.getId());

        if (decisionLine == null) {
            return CompletableFuture.failedFuture(
                    new AiParsingException("DecisionLine cannot be null"));
        }
        if (baseScenario == null) {
            return CompletableFuture.failedFuture(
                    new AiParsingException("BaseScenario cannot be null"));
        }

        try {
            // Step 1: 프롬프트 생성
            List<SceneType> baseSceneTypes = getBaseSceneTypes(baseScenario);
            String newScenarioPrompt = DecisionScenarioPrompt.generatePrompt(decisionLine, baseScenario, baseSceneTypes);
            log.debug("Generated decision scenario prompt for DecisionLine ID: {}", decisionLine.getId());

            // Step 2: AI 호출 및 파싱
            return textAiClient.generateText(newScenarioPrompt)
                    .thenApply(aiResponse -> {
                        try {
                            log.debug("Received AI response for DecisionLine ID: {}, length: {}",
                                    decisionLine.getId(), aiResponse.length());
                            return objectMapper.readValue(aiResponse, DecisionScenarioResult.class);
                        } catch (Exception e) {
                            log.error("Failed to parse DecisionScenario AI response for DecisionLine ID: {}, error: {}",
                                    decisionLine.getId(), e.getMessage(), e);
                            throw new AiParsingException("Failed to parse DecisionScenario response: " + e.getMessage());
                        }
                    });
        } catch (Exception e) {
            log.error("Error in generateDecisionScenario for DecisionLine ID: {}, error: {}",
                    decisionLine.getId(), e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    private List<SceneType> getBaseSceneTypes(Scenario baseScenario) {

        List<SceneType> sceneTypes = sceneTypeRepository.findByScenarioIdOrderByTypeAsc(baseScenario.getId());

        if (sceneTypes.isEmpty()) {
            log.warn("No SceneTypes found for Scenario ID: {}", baseScenario.getId());
        }

        return sceneTypes;
    }

    @Override
    public CompletableFuture<String> generateSituation(List<DecisionNode> previousNodes) {
        log.info("Generating situation based on {} previous decision nodes",
                 previousNodes != null ? previousNodes.size() : 0);

        // Input validation
        if (previousNodes == null || previousNodes.isEmpty()) {
            return CompletableFuture.failedFuture(
                    new AiParsingException("Previous nodes cannot be null or empty for situation generation"));
        }

        // Validate node data quality
        if (!SituationPrompt.validatePreviousNodes(previousNodes)) {
            return CompletableFuture.failedFuture(
                    new AiParsingException("Previous nodes contain invalid data (missing situation or decision)"));
        }

        try {
            // Step 1: 프롬프트 생성
            String situationPrompt = SituationPrompt.generatePrompt(previousNodes);
            log.debug("Generated situation prompt for {} nodes, estimated tokens: {}",
                     previousNodes.size(), SituationPrompt.estimateTokens(previousNodes));

            // Step 2: AI 호출 및 상황 텍스트 추출
            return textAiClient.generateText(situationPrompt)
                    .thenApply(aiResponse -> {
                        try {
                            log.debug("Received AI response for situation generation, length: {}",
                                     aiResponse.length());

                            // Step 3: JSON에서 상황 텍스트만 추출
                            String situation = SituationPrompt.extractSituation(aiResponse);

                            // 추천 선택지는 로깅만 (Trees 도메인에서 별도 처리)
                            String recommendedOption = SituationPrompt.extractRecommendedOption(aiResponse);
                            if (recommendedOption != null) {
                                log.debug("AI also provided recommended option: {}", recommendedOption);
                            }

                            return situation;
                        } catch (Exception e) {
                            log.error("Failed to parse situation AI response, error: {}",
                                     e.getMessage(), e);
                            throw new AiParsingException("Failed to parse situation response: " + e.getMessage());
                        }
                    });
        } catch (Exception e) {
            log.error("Error in generateSituation for {} nodes, error: {}",
                     previousNodes.size(), e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }
}
