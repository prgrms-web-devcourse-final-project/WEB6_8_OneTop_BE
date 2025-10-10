package com.back.global.ai.client.image;

import com.back.global.ai.config.ImageAiConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * StableDiffusionImageClient 테스트
 *
 * Stable Diffusion 3.5 Large Turbo API 호출 및 Base64 이미지 응답 처리를 검증합니다.
 * - 이미지 생성 API 호출 및 응답 파싱
 * - 에러 처리 및 재시도 로직
 * - 활성화/비활성화 상태 관리
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("StableDiffusionImageClient 테스트")
class StableDiffusionImageClientTest {

    @Mock
    private ImageAiConfig imageAiConfig;

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private StableDiffusionImageClient stableDiffusionImageClient;

    @BeforeEach
    void setUp() {
        given(imageAiConfig.isEnabled()).willReturn(true);
        given(imageAiConfig.getBaseUrl()).willReturn("https://api.stability.ai");
        given(imageAiConfig.getApiKey()).willReturn("test-api-key");
        given(imageAiConfig.getTimeoutSeconds()).willReturn(60);
        given(imageAiConfig.getMaxRetries()).willReturn(3);

        stableDiffusionImageClient = new StableDiffusionImageClient(imageAiConfig, webClient);
    }

    @Nested
    @DisplayName("이미지 생성 테스트")
    class GenerateImageTests {

        @Test
        @DisplayName("성공 - 유효한 프롬프트로 이미지 생성")
        void generateImage_Success() throws ExecutionException, InterruptedException {
            // Given
            String prompt = "A futuristic cityscape at sunset";
            String mockBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAUA...";
            String mockResponse = String.format(
                    "{\"images\": [{\"base64\": \"%s\", \"seed\": 12345}]}", mockBase64
            );

            given(webClient.post()).willReturn(requestBodyUriSpec);
            given(requestBodyUriSpec.uri(anyString())).willReturn(requestBodySpec);
            given(requestBodySpec.header(anyString(), anyString())).willReturn(requestBodySpec);
            given(requestBodySpec.contentType(any())).willReturn(requestBodySpec);
            given(requestBodySpec.bodyValue(any())).willReturn(requestHeadersSpec);
            given(requestHeadersSpec.retrieve()).willReturn(responseSpec);
            given(responseSpec.bodyToMono(String.class)).willReturn(Mono.just(mockResponse));

            // When
            CompletableFuture<String> result = stableDiffusionImageClient.generateImage(prompt);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.get()).isEqualTo(mockBase64);
            verify(webClient, times(1)).post();
        }

        @Test
        @DisplayName("성공 - 옵션과 함께 이미지 생성")
        void generateImage_WithOptions_Success() throws ExecutionException, InterruptedException {
            // Given
            String prompt = "A serene mountain landscape";
            Map<String, Object> options = Map.of(
                    "aspect_ratio", "16:9",
                    "seed", 999,
                    "negative_prompt", "cartoon, anime"
            );
            String mockBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAUA...";
            String mockResponse = String.format(
                    "{\"images\": [{\"base64\": \"%s\"}]}", mockBase64
            );

            given(webClient.post()).willReturn(requestBodyUriSpec);
            given(requestBodyUriSpec.uri(anyString())).willReturn(requestBodySpec);
            given(requestBodySpec.header(anyString(), anyString())).willReturn(requestBodySpec);
            given(requestBodySpec.contentType(any())).willReturn(requestBodySpec);
            given(requestBodySpec.bodyValue(any())).willReturn(requestHeadersSpec);
            given(requestHeadersSpec.retrieve()).willReturn(responseSpec);
            given(responseSpec.bodyToMono(String.class)).willReturn(Mono.just(mockResponse));

            // When
            CompletableFuture<String> result = stableDiffusionImageClient.generateImage(prompt, options);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.get()).isEqualTo(mockBase64);
        }

        @Test
        @DisplayName("실패 - API 응답 구조 오류")
        void generateImage_InvalidResponseStructure() {
            // Given
            String prompt = "Invalid response test";
            String invalidResponse = "{\"error\": \"Invalid API key\"}";

            given(webClient.post()).willReturn(requestBodyUriSpec);
            given(requestBodyUriSpec.uri(anyString())).willReturn(requestBodySpec);
            given(requestBodySpec.header(anyString(), anyString())).willReturn(requestBodySpec);
            given(requestBodySpec.contentType(any())).willReturn(requestBodySpec);
            given(requestBodySpec.bodyValue(any())).willReturn(requestHeadersSpec);
            given(requestHeadersSpec.retrieve()).willReturn(responseSpec);
            given(responseSpec.bodyToMono(String.class)).willReturn(Mono.just(invalidResponse));

            // When
            CompletableFuture<String> result = stableDiffusionImageClient.generateImage(prompt);

            // Then
            assertThat(result).isNotNull();
            assertThat(result).isCompletedExceptionally();
        }

        @Test
        @DisplayName("실패 - 네트워크 오류")
        void generateImage_NetworkError() {
            // Given
            String prompt = "Network error test";

            given(webClient.post()).willReturn(requestBodyUriSpec);
            given(requestBodyUriSpec.uri(anyString())).willReturn(requestBodySpec);
            given(requestBodySpec.header(anyString(), anyString())).willReturn(requestBodySpec);
            given(requestBodySpec.contentType(any())).willReturn(requestBodySpec);
            given(requestBodySpec.bodyValue(any())).willReturn(requestHeadersSpec);
            given(requestHeadersSpec.retrieve()).willReturn(responseSpec);
            given(responseSpec.bodyToMono(String.class))
                    .willReturn(Mono.error(new RuntimeException("Connection timeout")));

            // When
            CompletableFuture<String> result = stableDiffusionImageClient.generateImage(prompt);

            // Then
            assertThat(result).isNotNull();
            assertThat(result).isCompletedExceptionally();
        }
    }

    @Nested
    @DisplayName("활성화 여부 테스트")
    class IsEnabledTests {

        @Test
        @DisplayName("성공 - 이미지 AI 활성화 상태")
        void isEnabled_True() {
            // Given
            given(imageAiConfig.isEnabled()).willReturn(true);

            // When
            boolean result = stableDiffusionImageClient.isEnabled();

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("성공 - 이미지 AI 비활성화 상태")
        void isEnabled_False() {
            // Given
            given(imageAiConfig.isEnabled()).willReturn(false);

            // When
            boolean result = stableDiffusionImageClient.isEnabled();

            // Then
            assertThat(result).isFalse();
        }
    }
}
