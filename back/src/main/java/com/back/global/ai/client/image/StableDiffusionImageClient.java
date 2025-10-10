package com.back.global.ai.client.image;

import com.back.global.ai.config.ImageAiConfig;
import com.back.global.ai.exception.AiServiceException;
import com.back.global.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Stable Diffusion 3.5 Large Turbo 이미지 생성 클라이언트
 * Stability AI API를 사용하여 고품질 이미지를 생성합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ai.image", name = "enabled", havingValue = "true")
public class StableDiffusionImageClient implements ImageAiClient {

    private final ImageAiConfig imageAiConfig;
    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public CompletableFuture<String> generateImage(String prompt) {
        return generateImage(prompt, Map.of());
    }

    @Override
    public CompletableFuture<String> generateImage(String prompt, Map<String, Object> options) {
        log.info("Generating image with Stable Diffusion 3.5 Large Turbo. Prompt: {}", prompt);

        // Multipart 요청 바디 구성
        MultipartBodyBuilder bodyBuilder = buildMultipartRequestBody(prompt, options);

        return webClient.post()
                .uri(imageAiConfig.getBaseUrl() + "/v2beta/stable-image/generate/sd3")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + imageAiConfig.getApiKey())
                .header(HttpHeaders.ACCEPT, "application/json")
                .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(imageAiConfig.getTimeoutSeconds()))
                .doOnError(error -> log.error("Stable Diffusion API error: {}", error.getMessage()))
                .retryWhen(reactor.util.retry.Retry.fixedDelay(
                        imageAiConfig.getMaxRetries(),
                        Duration.ofSeconds(2)
                ))
                .flatMap(this::extractImageData)
                .toFuture();
    }

    @Override
    public boolean isEnabled() {
        return imageAiConfig.isEnabled();
    }

    /**
     * Stable Diffusion API Multipart 요청 바디를 구성합니다.
     * SD 3.5 Large Turbo는 multipart/form-data 형식을 사용합니다.
     */
    private MultipartBodyBuilder buildMultipartRequestBody(String prompt, Map<String, Object> options) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();

        // 만화풍 스타일 추가
        String enhancedPrompt = prompt + ", cartoon style, animated illustration, vibrant colors, clean lines";
        builder.part("prompt", enhancedPrompt);

        // 모델 지정 (SD 3.5 Large Turbo)
        builder.part("model", "sd3.5-large-turbo");

        // 출력 형식
        builder.part("output_format", "jpeg");

        // 옵션에서 값 추출 (기본값 사용)
        builder.part("aspect_ratio", options.getOrDefault("aspect_ratio", "1:1"));

        if (options.containsKey("seed")) {
            builder.part("seed", options.get("seed").toString());
        }

        // 네거티브 프롬프트 (품질 향상 + 실사 스타일 배제)
        String negativePrompt = options.containsKey("negative_prompt")
            ? options.get("negative_prompt").toString()
            : "blurry, low quality, distorted, deformed, realistic, photo, photography";
        builder.part("negative_prompt", negativePrompt);

        return builder;
    }

    /**
     * API 응답에서 이미지 데이터를 추출합니다.
     * SD 3.5 Large Turbo의 응답 구조: { "artifacts": [{ "base64": "...", "finishReason": "SUCCESS" }] }
     *
     * @param response API 응답 JSON
     * @return 이미지 Base64 데이터
     */
    private Mono<String> extractImageData(String response) {
        try {
            JsonNode rootNode = objectMapper.readTree(response);

            // Stability AI 공식 응답 구조: { "artifacts": [{ "base64": "..." }] }
            if (rootNode.has("artifacts") && rootNode.get("artifacts").isArray()) {
                JsonNode firstArtifact = rootNode.get("artifacts").get(0);

                // finishReason 검증
                if (firstArtifact.has("finishReason")) {
                    String finishReason = firstArtifact.get("finishReason").asText();
                    if (!"SUCCESS".equals(finishReason)) {
                        log.error("Image generation failed with reason: {}", finishReason);
                        return Mono.error(new AiServiceException(
                            ErrorCode.AI_GENERATION_FAILED,
                            "Image generation failed: " + finishReason
                        ));
                    }
                }

                // Base64 데이터 추출
                if (firstArtifact.has("base64")) {
                    String base64Data = firstArtifact.get("base64").asText();
                    log.info("Image generated successfully. Base64 length: {}", base64Data.length());
                    return Mono.just(base64Data);
                }
            }

            // 응답 구조가 예상과 다를 경우
            log.error("Unexpected Stable Diffusion API response structure: {}", response);
            return Mono.error(new AiServiceException(
                ErrorCode.AI_GENERATION_FAILED,
                "Failed to extract image data from API response"
            ));

        } catch (Exception e) {
            log.error("Error parsing Stable Diffusion API response: {}", e.getMessage());
            return Mono.error(new AiServiceException(
                ErrorCode.AI_GENERATION_FAILED,
                "Failed to parse image generation response: " + e.getMessage()
            ));
        }
    }
}
