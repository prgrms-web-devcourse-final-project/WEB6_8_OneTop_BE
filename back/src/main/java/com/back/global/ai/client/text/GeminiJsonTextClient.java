/*
 * 2.0 전용 JSON 강제 & 안전화(최소 변경) + VectorResponse 매핑
 */
package com.back.global.ai.client.text;

import com.back.global.ai.config.TextAiConfig;
import com.back.global.ai.dto.AiRequest;
import com.back.global.ai.dto.gemini.GeminiResponse;
import com.back.global.ai.dto.gemini.VectorResponse;
import com.back.global.ai.exception.AiApiException;
import com.back.global.ai.exception.AiParsingException;
import com.back.global.ai.exception.AiTimeoutException;
import com.back.global.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

@Component("gemini20JsonClient")
@Primary
@Slf4j
public class GeminiJsonTextClient implements TextAiClient {

    private final WebClient webClient;
    private final TextAiConfig textAiConfig;
    private final ObjectMapper objectMapper; // ← 추가

    public GeminiJsonTextClient(@Qualifier("geminiWebClient") WebClient webClient,
                                TextAiConfig textAiConfig,
                                ObjectMapper objectMapper) { // ← 추가
        this.webClient = webClient;
        this.textAiConfig = textAiConfig;
        this.objectMapper = objectMapper;   // ← 추가
    }

    @Override
    public CompletableFuture<String> generateText(String prompt) {
        return generateText(new AiRequest(prompt, Map.of()));
    }

    @Override
    public CompletableFuture<String> generateText(AiRequest aiRequest) {
        if (aiRequest == null || aiRequest.prompt() == null) {
            return CompletableFuture.failedFuture(new AiParsingException("Prompt is null"));
        }

        int ctxLimit = Math.max(4096, safeInt(textAiConfig.getMaxContextTokens(), 8192));
        int inTokens = estimateTokens(aiRequest.prompt());
        int userMaxOut = Math.max(1, aiRequest.maxTokens());
        int safety = 128;
        int minOut = 256;
        int roomForOut = Math.max(minOut, ctxLimit - inTokens - safety);
        int allowedOut = Math.max(minOut, Math.min(roomForOut, userMaxOut));

        String fittedPrompt = fitPromptToContext(aiRequest.prompt(), ctxLimit, allowedOut, safety);

        Map<String, Object> body = createGeminiRequest(aiRequest.parameters(), fittedPrompt, allowedOut);
        Mono<GeminiResponse> call = invoke(body);

        log.debug("[Gemini-2.0] ctxLimit={}, in≈{}, allowedOut={}, promptChars={}",
                ctxLimit, inTokens, allowedOut, aiRequest.prompt().length());

        return call
                .map(resp -> tryExtract(resp, false))
                .onErrorResume(e -> {
                    if (e instanceof AiParsingException apx &&
                            apx.getMessage() != null &&
                            apx.getMessage().contains("MAX_TOKENS")) {

                        log.warn("[Gemini-2.0] fallback retry due to MAX_TOKENS: {}", apx.getMessage());

                        int fallbackOut = Math.max(256, Math.min(userMaxOut * 2, 512));
                        String shortPrompt =
                                """
                                너는 JSON 출력기야. 아래 정보를 오직 JSON 객체 1개로만 반환해.
                                필드명: situation, recommendedOption (각 값은 한국어 1문장, 20자 이내)
                                마크다운/코드펜스/설명/추가텍스트 금지. JSON만!
                                ---
                                """ + safeHead(fittedPrompt, 800);

                        Map<String, Object> fallbackBody = createGeminiRequest(aiRequest.parameters(), shortPrompt, fallbackOut);
                        return invoke(fallbackBody).map(resp -> tryExtract(resp, true));
                    }
                    return Mono.error(e);
                })
                .timeout(Duration.ofSeconds(textAiConfig.getTimeoutSeconds()))
                .onErrorMap(TimeoutException.class,
                        t -> new AiTimeoutException("Gemini request timeout after "
                                + textAiConfig.getTimeoutSeconds() + "s"))
                .retryWhen(
                        Retry.backoff(textAiConfig.getMaxRetries(),
                                        Duration.ofSeconds(textAiConfig.getRetryDelaySeconds()))
                                .filter(this::isTransient)
                )
                .doOnError(e -> log.error("Gemini API call failed: {}", safeTruncate(e.toString(), 2000)))
                .toFuture();
    }

    /** 편의 메서드: 바로 VectorResponse DTO로 받기 */
    public CompletableFuture<VectorResponse> generateVector(AiRequest aiRequest) {
        return generateText(aiRequest).thenApply(this::toVectorResponse);
    }

    // --- 내부 호출/파싱 ---

    private boolean isTransient(Throwable ex) {
        if (ex instanceof IOException || ex instanceof TimeoutException) return true;
        if (ex instanceof WebClientResponseException w && w.getStatusCode().is5xxServerError()) return true;
        return false;
    }

    private Mono<GeminiResponse> invoke(Map<String, Object> body) {
        return webClient.post()
                .uri("/v1beta/models/{model}:generateContent", textAiConfig.getModel20())
                .header("x-goog-api-key", textAiConfig.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::handleErrorResponse)
                .bodyToMono(GeminiResponse.class);
    }

    // ===== 최소 변경 JSON 강제 =====
    private Map<String, Object> createGeminiRequest(Map<String, Object> params, String prompt, int outTokens) {
        Map<String, Object> gen = new HashMap<>();
        // 2.0: 빠른/일관 응답 위주
        gen.put("temperature", 0.2);
        gen.put("topK", 1);
        gen.put("topP", 0.9);
        gen.put("candidateCount", 1);
        gen.put("maxOutputTokens", 120);

        // JSON 강제 (camel/snake 모두 세팅)
        gen.put("response_mime_type", "application/json");
        gen.put("responseMimeType", "application/json");

        Map<String, Object> responseSchema = Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                        "situation", Map.of("type", "STRING"),
                        "recommendedOption", Map.of("type", "STRING")
                ),
                "required", List.of("situation", "recommendedOption")
        );
        gen.put("response_schema", responseSchema);
        gen.put("responseSchema", responseSchema);

        // 외부 파라미터(화이트리스트) — camel/snake 모두 허용
        if (params != null) {
            copyIfPresent(params, gen, "temperature");
            copyIfPresent(params, gen, "topK");
            copyIfPresent(params, gen, "topP");
            copyIfPresent(params, gen, "maxOutputTokens");
            copyIfPresent(params, gen, "candidateCount");

            // MIME / 스키마 키 양쪽 지원
            copyIfPresent(params, gen, "responseMimeType");
            copyIfPresent(params, gen, "response_mime_type");
            copyIfPresent(params, gen, "responseSchema");
            copyIfPresent(params, gen, "response_schema");
            // stopSequences는 조기 컷 리스크로 미사용
        }

        Map<String, Object> userContent = Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", prompt))
        );

        // JSON-only 시스템 규율
        Map<String, Object> systemInstruction = Map.of(
                "parts", List.of(Map.of("text",
                        "You are a JSON emitter. Output ONLY one compact JSON object that matches the schema. " +
                                "No markdown, no code fences, no explanations. Keys: situation, recommendedOption."))
        );

        Map<String, Object> body = new HashMap<>();
        body.put("systemInstruction", systemInstruction);
        body.put("contents", List.of(userContent));
        body.put("generationConfig", gen);
        body.put("toolConfig", Map.of("functionCallingConfig", Map.of("mode", "NONE")));
        return body;
    }
    // ==========================

    private void copyIfPresent(Map<String, Object> src, Map<String, Object> dst, String key) {
        Object v = src.get(key);
        if (v != null) dst.put(key, v);
    }

    private String extractContent(GeminiResponse response) {
        if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
            log.warn("[Gemini] empty candidates: body=null/empty");
            throw new AiParsingException("No candidates in Gemini response");
        }
        var c = response.candidates().get(0);
        var finish = c.finishReason();
        if ("SAFETY".equalsIgnoreCase(finish)) {
            log.warn("[Gemini] content blocked by safety filters. finishReason=SAFETY");
            throw new AiParsingException("Content blocked by safety filters");
        }
        if (c.content() == null || c.content().parts() == null || c.content().parts().isEmpty()) {
            String reason = finish != null ? " (" + finish + ")" : "";
            log.warn("[Gemini] no parts in candidate content. finishReason={}", finish);
            throw new AiParsingException("No parts in candidate content" + reason);
        }
        String text = c.content().parts().get(0).text();
        if (text == null || text.isBlank()) {
            String reason = finish != null ? " (" + finish + ")" : "";
            log.warn("[Gemini] blank text part. finishReason={}", finish);
            throw new AiParsingException("Blank text part" + reason);
        }
        return sanitizeJsonText(text);
    }

    private String tryExtract(GeminiResponse response, boolean fallback) {
        try {
            return extractContent(response);
        } catch (AiParsingException e) {
            String finish = null;
            if (response != null && response.candidates() != null && !response.candidates().isEmpty()) {
                finish = response.candidates().get(0).finishReason();
            }
            if ("MAX_TOKENS".equalsIgnoreCase(finish)) {
                String msg = "No parts in candidate content (MAX_TOKENS)";
                if (fallback) throw new AiParsingException(msg);
                throw new AiParsingException(msg);
            }
            throw e;
        }
    }

    private Mono<? extends Throwable> handleErrorResponse(ClientResponse response) {
        return response.bodyToMono(String.class)
                .map(errorBody -> {
                    log.warn("[Gemini] HTTP error: status={}, body={}",
                            response.statusCode(), safeTruncate(errorBody, 2000));
                    return new AiApiException(
                            ErrorCode.AI_SERVICE_UNAVAILABLE,
                            "Gemini API call failed: " + response.statusCode()
                    );
                });
    }

    // --- utils ---

    private int estimateTokens(String s) {
        if (s == null || s.isEmpty()) return 0;
        int len = s.length();
        int han = 0;
        for (int i = 0; i < Math.min(len, 4000); i++) {
            char ch = s.charAt(i);
            if (Character.UnicodeBlock.of(ch) == Character.UnicodeBlock.HANGUL_SYLLABLES
                    || Character.UnicodeBlock.of(ch) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS) {
                han++;
            }
        }
        double hanRatio = (len == 0) ? 0 : (double) han / Math.min(len, 4000);
        int div = (hanRatio > 0.3) ? 2 : 4;
        return Math.max(1, len / div);
    }

    private String fitPromptToContext(String prompt, int ctxLimit, int outTokens, int safety) {
        int in = estimateTokens(prompt);
        int limitIn = Math.max(256, ctxLimit - outTokens - safety);
        if (in <= limitIn) return prompt;

        int targetChars = Math.max(200, (int) (prompt.length() * (limitIn / (double) in)));
        int head = Math.min(prompt.length(), (int) (targetChars * 0.7));
        int tail = Math.min(prompt.length() - head, (int) (targetChars * 0.3));
        return prompt.substring(0, head) + "\n...\n" + prompt.substring(prompt.length() - tail);
    }

    private static String safeHead(String s, int maxChars) {
        if (s == null) return "";
        if (s.length() <= maxChars) return s;
        return s.substring(0, maxChars) + "\n...";
    }

    private static int safeInt(Integer v, int defVal) {
        return (v == null || v <= 0) ? defVal : v;
    }

    private static String safeTruncate(String s, int max) {
        return (s == null || s.length() <= max) ? s : s.substring(0, max) + "...";
    }

    // ```json ... ``` 래핑 제거 및 앞뒤 잡설 제거: 첫 완전한 JSON 객체만 반환
    private static String sanitizeJsonText(String raw) {
        String s = raw.trim();
        if (s.startsWith("```")) {
            int first = s.indexOf('\n');
            int last = s.lastIndexOf("```");
            if (first >= 0 && last > first) {
                s = s.substring(first + 1, last).trim();
            }
        }
        int depth = 0, start = -1;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{') { if (depth == 0) start = i; depth++; }
            else if (c == '}') { depth--; if (depth == 0 && start >= 0) { return s.substring(start, i + 1); } }
        }
        return s;
    }

    // JSON 문자열 → VectorResponse
    private VectorResponse toVectorResponse(String json) {
        try {
            String clean = sanitizeJsonText(json);
            VectorResponse dto = objectMapper.readValue(clean, VectorResponse.class);
            if (dto.situation() == null || dto.situation().isBlank()
                    || dto.recommendedOption() == null || dto.recommendedOption().isBlank()) {
                throw new AiParsingException("Missing required fields in VectorResponse");
            }
            return dto;
        } catch (JsonProcessingException e) {
            throw new AiParsingException("Failed to parse VectorResponse: " + e.getOriginalMessage());
        }
    }
}
