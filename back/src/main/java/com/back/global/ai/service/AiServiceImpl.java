package com.back.global.ai.service;

import com.back.domain.node.entity.BaseLine;
import com.back.domain.node.entity.DecisionLine;
import com.back.domain.node.entity.DecisionNode;
import com.back.domain.scenario.entity.Scenario;
import com.back.domain.scenario.entity.SceneType;
import com.back.domain.scenario.repository.SceneTypeRepository;
import com.back.global.ai.client.image.ImageAiClient;
import com.back.global.ai.client.text.TextAiClient;
import com.back.global.ai.config.BaseScenarioAiProperties;
import com.back.global.ai.config.DecisionScenarioAiProperties;
import com.back.global.ai.config.SituationAiProperties;
import com.back.global.ai.dto.AiRequest;
import com.back.global.ai.dto.result.BaseScenarioResult;
import com.back.global.ai.dto.result.DecisionScenarioResult;
import com.back.global.ai.exception.AiParsingException;
import com.back.global.ai.exception.AiServiceException;
import com.back.global.ai.prompt.BaseScenarioPrompt;
import com.back.global.ai.prompt.DecisionScenarioPrompt;
import com.back.global.ai.prompt.SituationPrompt;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * AI 서비스 구현체.
 * Gemini API를 사용하여 시나리오 생성, 상황 생성 등의 AI 기반 기능을 제공합니다.
 */

@Service
@RequiredArgsConstructor
@Slf4j
public class AiServiceImpl implements AiService {

    private final @Qualifier("gemini25TextClient") TextAiClient textAiClient;
    private final ObjectMapper objectMapper;
    private final SceneTypeRepository sceneTypeRepository;
    private final SituationAiProperties situationAiProperties;
    private final BaseScenarioAiProperties baseScenarioAiProperties;
    private final DecisionScenarioAiProperties decisionScenarioAiProperties;
    private final ImageAiClient imageAiClient;
    private final com.back.global.storage.StorageService storageService;

    @Override
    public CompletableFuture<BaseScenarioResult> generateBaseScenario(BaseLine baseLine) {
        if (baseLine == null) {
            return CompletableFuture.failedFuture(
                    new AiServiceException(com.back.global.exception.ErrorCode.AI_INVALID_REQUEST, "BaseLine cannot be null"));
        }

        log.info("Generating base scenario for BaseLine ID: {}", baseLine.getId());

        try {
            // Step 1: 프롬프트 생성
            String baseScenarioPrompt = BaseScenarioPrompt.generatePrompt(baseLine);
            log.debug("Generated base scenario prompt for BaseLine ID: {}", baseLine.getId());

            // Step 2: AI 호출 및 파싱
            int maxTokens = baseScenarioAiProperties.getMaxOutputTokens();
            log.info("Using maxOutputTokens: {} for base scenario generation", maxTokens);
            AiRequest request = new AiRequest(baseScenarioPrompt, Map.of(), maxTokens);
            return textAiClient.generateText(request)
                    .thenApply(aiResponse -> {
                        try {
                            log.debug("Received AI response for BaseLine ID: {}, length: {}",
                                    baseLine.getId(), aiResponse.length());
                            // Remove markdown code block wrappers (```json ... ```)
                            String cleanedResponse = aiResponse.trim();
                            if (cleanedResponse.startsWith("```json")) {
                                cleanedResponse = cleanedResponse.substring(7); // Remove ```json
                            } else if (cleanedResponse.startsWith("```")) {
                                cleanedResponse = cleanedResponse.substring(3); // Remove ```
                            }
                            if (cleanedResponse.endsWith("```")) {
                                cleanedResponse = cleanedResponse.substring(0, cleanedResponse.length() - 3);
                            }
                            cleanedResponse = cleanedResponse.trim();

                            log.info("Cleaned AI response for BaseLine ID: {}: {}", baseLine.getId(), cleanedResponse);
                            return objectMapper.readValue(cleanedResponse, BaseScenarioResult.class);
                        } catch (Exception e) {
                            log.error("Failed to parse BaseScenario AI response for BaseLine ID: {}, error: {}",
                                    baseLine.getId(), e.getMessage(), e);
                            throw new AiParsingException("Failed to parse BaseScenario response: " + e.getMessage());
                        }
                    })
                    .exceptionally(e -> {
                        log.error("AI generation failed for BaseLine ID: {}, error: {}",
                                baseLine.getId(), e.getMessage(), e);
                        // AiParsingException은 그대로 전파, 나머지만 AiServiceException으로 감쌈
                        Throwable cause = e.getCause() != null ? e.getCause() : e;
                        if (cause instanceof AiParsingException) {
                            throw (AiParsingException) cause;
                        }
                        throw new AiServiceException(com.back.global.exception.ErrorCode.AI_GENERATION_FAILED,
                                "Failed to generate base scenario: " + e.getMessage());
                    });
        } catch (Exception e) {
            log.error("Error in generateBaseScenario for BaseLine ID: {}, error: {}",
                    baseLine.getId(), e.getMessage(), e);
            return CompletableFuture.failedFuture(
                    new AiServiceException(com.back.global.exception.ErrorCode.AI_GENERATION_FAILED,
                            "Unexpected error in base scenario generation: " + e.getMessage()));
        }
    }

    @Override
    public CompletableFuture<DecisionScenarioResult> generateDecisionScenario(DecisionLine decisionLine, Scenario baseScenario) {
        if (decisionLine == null) {
            return CompletableFuture.failedFuture(
                    new AiServiceException(com.back.global.exception.ErrorCode.AI_INVALID_REQUEST, "DecisionLine cannot be null"));
        }
        if (baseScenario == null) {
            return CompletableFuture.failedFuture(
                    new AiServiceException(com.back.global.exception.ErrorCode.AI_INVALID_REQUEST, "BaseScenario cannot be null"));
        }

        log.info("Generating Decision scenario for DecisionLine ID: {}", decisionLine.getId());

        try {
            // Step 1: 프롬프트 생성
            List<SceneType> baseSceneTypes = getBaseSceneTypes(baseScenario);
            String newScenarioPrompt = DecisionScenarioPrompt.generatePrompt(decisionLine, baseScenario, baseSceneTypes);
            log.debug("Generated decision scenario prompt for DecisionLine ID: {}", decisionLine.getId());

            // Step 2: AI 호출 및 파싱
            AiRequest request = new AiRequest(newScenarioPrompt, Map.of(), decisionScenarioAiProperties.getMaxOutputTokens());
            return textAiClient.generateText(request)
                    .thenApply(aiResponse -> {
                        try {
                            log.debug("Received AI response for DecisionLine ID: {}, length: {}",
                                    decisionLine.getId(), aiResponse.length());
                            // Remove markdown code block wrappers (```json ... ```)
                            String cleanedResponse = aiResponse.trim();
                            if (cleanedResponse.startsWith("```json")) {
                                cleanedResponse = cleanedResponse.substring(7);
                            } else if (cleanedResponse.startsWith("```")) {
                                cleanedResponse = cleanedResponse.substring(3);
                            }
                            if (cleanedResponse.endsWith("```")) {
                                cleanedResponse = cleanedResponse.substring(0, cleanedResponse.length() - 3);
                            }
                            cleanedResponse = cleanedResponse.trim();

                            log.info("Cleaned AI response for DecisionLine ID: {}: {}", decisionLine.getId(), cleanedResponse);
                            return objectMapper.readValue(cleanedResponse, DecisionScenarioResult.class);
                        } catch (Exception e) {
                            log.error("Failed to parse DecisionScenario AI response for DecisionLine ID: {}, error: {}",
                                    decisionLine.getId(), e.getMessage(), e);
                            throw new AiParsingException("Failed to parse DecisionScenario response: " + e.getMessage());
                        }
                    })
                    .exceptionally(e -> {
                        log.error("AI generation failed for DecisionLine ID: {}, error: {}",
                                decisionLine.getId(), e.getMessage(), e);
                        // AiParsingException은 그대로 전파, 나머지만 AiServiceException으로 감쌈
                        Throwable cause = e.getCause() != null ? e.getCause() : e;
                        if (cause instanceof AiParsingException) {
                            throw (AiParsingException) cause;
                        }
                        throw new AiServiceException(com.back.global.exception.ErrorCode.AI_GENERATION_FAILED,
                                "Failed to generate decision scenario: " + e.getMessage());
                    });
        } catch (Exception e) {
            log.error("Error in generateDecisionScenario for DecisionLine ID: {}, error: {}",
                    decisionLine.getId(), e.getMessage(), e);
            return CompletableFuture.failedFuture(
                    new AiServiceException(com.back.global.exception.ErrorCode.AI_GENERATION_FAILED,
                            "Unexpected error in decision scenario generation: " + e.getMessage()));
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
                    new AiServiceException(com.back.global.exception.ErrorCode.AI_INVALID_REQUEST,
                            "Previous nodes cannot be null or empty for situation generation"));
        }

        // Validate node data quality
        if (!SituationPrompt.validatePreviousNodes(previousNodes)) {
            return CompletableFuture.failedFuture(
                    new AiServiceException(com.back.global.exception.ErrorCode.AI_INVALID_REQUEST,
                            "Previous nodes contain invalid data (missing situation or decision)"));
        }

        try {
            // Step 1: 프롬프트 생성
            String situationPrompt = SituationPrompt.generatePrompt(previousNodes);
            log.debug("Generated situation prompt for {} nodes, estimated tokens: {}",
                     previousNodes.size(), SituationPrompt.estimateTokens(previousNodes));

            // Step 2: AI 호출 및 상황 텍스트 추출
            AiRequest request = new AiRequest(situationPrompt, Map.of(), situationAiProperties.getMaxOutputTokens());
            return textAiClient.generateText(request)
                    .thenApply(aiResponse -> {
                        try {
                            log.debug("Received AI response for situation generation, length: {}",
                                     aiResponse.length());

                            // Step 3: JSON에서 상황 텍스트만 추출
                            String situation = SituationPrompt.extractSituation(aiResponse, objectMapper);

                            // 추천 선택지는 로깅만 (Trees 도메인에서 별도 처리)
                            String recommendedOption = SituationPrompt.extractRecommendedOption(aiResponse, objectMapper);
                            if (recommendedOption != null) {
                                log.debug("AI also provided recommended option: {}", recommendedOption);
                            }

                            return situation;
                        } catch (Exception e) {
                            log.error("Failed to parse situation AI response, error: {}",
                                     e.getMessage(), e);
                            throw new AiParsingException("Failed to parse situation response: " + e.getMessage());
                        }
                    })
                    .exceptionally(e -> {
                        log.error("AI generation failed for situation, error: {}",
                                e.getMessage(), e);
                        // AiParsingException은 그대로 전파, 나머지만 AiServiceException으로 감쌈
                        Throwable cause = e.getCause() != null ? e.getCause() : e;
                        if (cause instanceof AiParsingException) {
                            throw (AiParsingException) cause;
                        }
                        throw new AiServiceException(com.back.global.exception.ErrorCode.AI_GENERATION_FAILED,
                                "Failed to generate situation: " + e.getMessage());
                    });
        } catch (Exception e) {
            log.error("Error in generateSituation for {} nodes, error: {}",
                     previousNodes.size(), e.getMessage(), e);
            return CompletableFuture.failedFuture(
                    new AiServiceException(com.back.global.exception.ErrorCode.AI_GENERATION_FAILED,
                            "Unexpected error in situation generation: " + e.getMessage()));
        }
    }

    @Override
    public CompletableFuture<String> generateImage(String prompt) {
        if (!imageAiClient.isEnabled()) {
            log.warn("Image AI is disabled. Returning placeholder.");
            return CompletableFuture.completedFuture("placeholder-image-url");
        }

        if (prompt == null || prompt.trim().isEmpty()) {
            log.warn("Image prompt is empty. Returning placeholder.");
            return CompletableFuture.completedFuture("placeholder-image-url");
        }

        log.info("Generating image with prompt: {} (Storage: {})", prompt, storageService.getStorageType());

        try {
            // Stable Diffusion API 호출 → Base64 이미지 생성
            return imageAiClient.generateImage(prompt)
                    .thenCompose(base64Data -> {
                        // Base64 데이터를 스토리지에 업로드 → URL 반환
                        if (base64Data == null || base64Data.isEmpty() || "placeholder-image-url".equals(base64Data)) {
                            log.warn("Empty or placeholder Base64 data received from image AI");
                            return CompletableFuture.completedFuture("placeholder-image-url");
                        }

                        log.info("Image generated successfully (Base64 length: {}), uploading to {} storage...",
                                base64Data.length(), storageService.getStorageType());

                        return storageService.uploadBase64Image(base64Data);
                    })
                    .exceptionally(e -> {
                        log.warn("Failed to generate or upload image, returning placeholder: {}", e.getMessage());
                        return "placeholder-image-url";
                    });
        } catch (Exception e) {
            log.warn("Error in generateImage, returning placeholder: {}", e.getMessage());
            return CompletableFuture.completedFuture("placeholder-image-url");
        }
    }
}
