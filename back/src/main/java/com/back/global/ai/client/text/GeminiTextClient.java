package com.back.global.ai.client.text;

import com.back.global.ai.config.TextAiConfig;
import com.back.global.ai.dto.AiRequest;
import com.back.global.ai.dto.gemini.GeminiResponse;
import com.back.global.ai.exception.AiApiException;
import com.back.global.ai.exception.AiParsingException;
import com.back.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Gemini AI 텍스트 생성 클라이언트
 * Google Gemini API를 통한 비동기 텍스트 생성, 재시도, 에러 처리를 담당합니다.
 */
@Component
@Slf4j
public class GeminiTextClient implements TextAiClient {

    private final WebClient webClient;
    private final TextAiConfig textAiConfig;

    public GeminiTextClient(@Qualifier("geminiWebClient") WebClient webClient,
                           TextAiConfig textAiConfig) {
        this.webClient = webClient;
        this.textAiConfig = textAiConfig;
    }

    @Override
    public CompletableFuture<String> generateText(String prompt) {
        return generateText(new AiRequest(prompt, Map.of()));
    }

    @Override
    public CompletableFuture<String> generateText(AiRequest aiRequest) {
        return webClient
            .post()
            .uri("/v1beta/models/{model}:generateContent?key={apiKey}",
                 textAiConfig.getModel(), textAiConfig.getApiKey())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(createGeminiRequest(aiRequest.prompt(), aiRequest.maxTokens()))
            .retrieve()
            .onStatus(HttpStatusCode::isError, this::handleErrorResponse)
            .bodyToMono(GeminiResponse.class)
            .map(this::extractContent)
            .timeout(Duration.ofSeconds(textAiConfig.getTimeoutSeconds()))
            .retryWhen(Retry.backoff(textAiConfig.getMaxRetries(), Duration.ofSeconds(2)))
            .doOnError(error -> log.error("Gemini API call failed: {}", error.getMessage()))
            .toFuture();
    }

    private Map<String, Object> createGeminiRequest(String prompt, int maxTokens) {
        return Map.of(
            "contents", List.of(
                Map.of("parts", List.of(Map.of("text", prompt)))
            ),
            "generationConfig", Map.of(
                "temperature", 0.7,
                "topK", 40,
                "topP", 0.95,
                "maxOutputTokens", maxTokens  // AiRequest의 maxTokens 사용
            )
        );
    }

    private String extractContent(GeminiResponse response) {
        try {
            // candidates가 비어있는 경우
            if (response.candidates().isEmpty()) {
                throw new AiParsingException("No candidates in Gemini response");
            }

            GeminiResponse.Candidate candidate = response.candidates().get(0);

            // 안전성 필터에 걸린 경우
            if ("SAFETY".equals(candidate.finishReason())) {
                throw new AiParsingException("Content blocked by safety filters");
            }

            // 내용이 없는 경우
            if (candidate.content().parts().isEmpty()) {
                throw new AiParsingException("No parts in candidate content");
            }

            return candidate.content().parts().get(0).text();
        } catch (Exception e) {
            throw new AiParsingException("Failed to extract content from Gemini response: " + e.getMessage());
        }
    }

    private Mono<? extends Throwable> handleErrorResponse(ClientResponse response) {
        return response.bodyToMono(String.class)
            .map(errorBody -> {
                log.error("Gemini API error: {} - {}", response.statusCode(), errorBody);
                return new AiApiException(ErrorCode.AI_SERVICE_UNAVAILABLE,
                    "Gemini API call failed: " + response.statusCode());
            });
    }
}
