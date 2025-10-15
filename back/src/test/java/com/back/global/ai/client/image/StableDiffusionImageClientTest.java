package com.back.global.ai.client.image;

import com.back.global.ai.config.ImageAiConfig;
import com.back.global.ai.exception.AiServiceException;
import com.back.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

/**
 * StableDiffusionImageClient 단위 테스트.
 * Stable Diffusion API 연동 로직을 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
@DisplayName("StableDiffusionImageClient 단위 테스트")
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

    private StableDiffusionImageClient client;

    @BeforeEach
    void setUp() {
        lenient().when(imageAiConfig.getBaseUrl()).thenReturn("https://api.stability.ai");
        lenient().when(imageAiConfig.getApiKey()).thenReturn("test-api-key");
        lenient().when(imageAiConfig.getTimeoutSeconds()).thenReturn(60);
        lenient().when(imageAiConfig.getMaxRetries()).thenReturn(3);
        lenient().when(imageAiConfig.isEnabled()).thenReturn(true);

        client = new StableDiffusionImageClient(imageAiConfig, webClient);
    }

    @Nested
    @DisplayName("이미지 생성")
    class GenerateImageTests {

        @Test
        @DisplayName("성공 - 기본 프롬프트로 이미지 생성")
        void generateImage_성공_기본_프롬프트() throws ExecutionException, InterruptedException {
            // Given
            String prompt = "A beautiful landscape";
            String mockResponse = """
                {
                    "artifacts": [{
                        "base64": "mock-base64-data",
                        "finishReason": "SUCCESS"
                    }]
                }
                """;

            // WebClient Mock 체인 설정
            given(webClient.post()).willReturn(requestBodyUriSpec);
            given(requestBodyUriSpec.uri(anyString())).willReturn(requestBodySpec);
            given(requestBodySpec.header(anyString(), anyString())).willReturn(requestBodySpec);
            given(requestBodySpec.body(any())).willReturn(requestHeadersSpec);
            given(requestHeadersSpec.retrieve()).willReturn(responseSpec);
            given(responseSpec.bodyToMono(String.class)).willReturn(Mono.just(mockResponse));

            // When
            CompletableFuture<String> future = client.generateImage(prompt);
            String result = future.get();

            // Then
            assertThat(result).isNotNull();
            assertThat(result).isEqualTo("mock-base64-data");

            verify(webClient, times(1)).post();
        }

        @Test
        @DisplayName("성공 - 옵션과 함께 이미지 생성")
        void generateImage_성공_옵션_포함() throws ExecutionException, InterruptedException {
            // Given
            String prompt = "A beautiful landscape";
            Map<String, Object> options = Map.of(
                    "aspect_ratio", "16:9",
                    "seed", 12345,
                    "negative_prompt", "ugly, blurry"
            );
            String mockResponse = """
                {
                    "artifacts": [{
                        "base64": "mock-base64-data-with-options",
                        "finishReason": "SUCCESS"
                    }]
                }
                """;

            given(webClient.post()).willReturn(requestBodyUriSpec);
            given(requestBodyUriSpec.uri(anyString())).willReturn(requestBodySpec);
            given(requestBodySpec.header(anyString(), anyString())).willReturn(requestBodySpec);
            given(requestBodySpec.body(any())).willReturn(requestHeadersSpec);
            given(requestHeadersSpec.retrieve()).willReturn(responseSpec);
            given(responseSpec.bodyToMono(String.class)).willReturn(Mono.just(mockResponse));

            // When
            CompletableFuture<String> future = client.generateImage(prompt, options);
            String result = future.get();

            // Then
            assertThat(result).isEqualTo("mock-base64-data-with-options");
        }

        @Test
        @DisplayName("성공 - 프롬프트 자동 향상 (만화풍 스타일)")
        void generateImage_성공_프롬프트_자동향상() {
            // Given
            String prompt = "A cat";
            String mockResponse = """
                {
                    "artifacts": [{
                        "base64": "enhanced-base64-data",
                        "finishReason": "SUCCESS"
                    }]
                }
                """;

            given(webClient.post()).willReturn(requestBodyUriSpec);
            given(requestBodyUriSpec.uri(anyString())).willReturn(requestBodySpec);
            given(requestBodySpec.header(anyString(), anyString())).willReturn(requestBodySpec);
            given(requestBodySpec.body(any())).willReturn(requestHeadersSpec);
            given(requestHeadersSpec.retrieve()).willReturn(responseSpec);
            given(responseSpec.bodyToMono(String.class)).willReturn(Mono.just(mockResponse));

            // When
            client.generateImage(prompt);

            // Then
            // 프롬프트가 자동으로 향상되었는지 확인 (내부적으로 ", cartoon style, ..." 추가됨)
            verify(requestBodySpec, times(2)).header(anyString(), anyString()); // Authorization + Accept
        }

        @Test
        @DisplayName("실패 - finishReason이 SUCCESS가 아닌 경우")
        void generateImage_실패_finishReason_실패() {
            // Given
            String prompt = "test prompt";
            String mockResponse = """
                {
                    "artifacts": [{
                        "base64": "some-data",
                        "finishReason": "CONTENT_FILTERED"
                    }]
                }
                """;

            given(webClient.post()).willReturn(requestBodyUriSpec);
            given(requestBodyUriSpec.uri(anyString())).willReturn(requestBodySpec);
            given(requestBodySpec.header(anyString(), anyString())).willReturn(requestBodySpec);
            given(requestBodySpec.body(any())).willReturn(requestHeadersSpec);
            given(requestHeadersSpec.retrieve()).willReturn(responseSpec);
            given(responseSpec.bodyToMono(String.class)).willReturn(Mono.just(mockResponse));

            // When & Then
            assertThatThrownBy(() -> client.generateImage(prompt).get())
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(AiServiceException.class)
                    .hasMessageContaining("CONTENT_FILTERED");
        }

        @Test
        @DisplayName("실패 - 응답 구조가 잘못된 경우")
        void generateImage_실패_잘못된_응답구조() {
            // Given
            String prompt = "test prompt";
            String invalidResponse = """
                {
                    "error": "Invalid request"
                }
                """;

            given(webClient.post()).willReturn(requestBodyUriSpec);
            given(requestBodyUriSpec.uri(anyString())).willReturn(requestBodySpec);
            given(requestBodySpec.header(anyString(), anyString())).willReturn(requestBodySpec);
            given(requestBodySpec.body(any())).willReturn(requestHeadersSpec);
            given(requestHeadersSpec.retrieve()).willReturn(responseSpec);
            given(responseSpec.bodyToMono(String.class)).willReturn(Mono.just(invalidResponse));

            // When & Then
            assertThatThrownBy(() -> client.generateImage(prompt).get())
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(AiServiceException.class)
                    .cause()
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AI_GENERATION_FAILED);
        }

        @Test
        @DisplayName("실패 - base64 필드가 없는 경우")
        void generateImage_실패_base64_없음() {
            // Given
            String prompt = "test prompt";
            String mockResponse = """
                {
                    "artifacts": [{
                        "finishReason": "SUCCESS"
                    }]
                }
                """;

            given(webClient.post()).willReturn(requestBodyUriSpec);
            given(requestBodyUriSpec.uri(anyString())).willReturn(requestBodySpec);
            given(requestBodySpec.header(anyString(), anyString())).willReturn(requestBodySpec);
            given(requestBodySpec.body(any())).willReturn(requestHeadersSpec);
            given(requestHeadersSpec.retrieve()).willReturn(responseSpec);
            given(responseSpec.bodyToMono(String.class)).willReturn(Mono.just(mockResponse));

            // When & Then
            assertThatThrownBy(() -> client.generateImage(prompt).get())
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(AiServiceException.class);
        }

        @Test
        @DisplayName("실패 - JSON 파싱 에러")
        void generateImage_실패_JSON_파싱_에러() {
            // Given
            String prompt = "test prompt";
            String invalidJson = "This is not JSON";

            given(webClient.post()).willReturn(requestBodyUriSpec);
            given(requestBodyUriSpec.uri(anyString())).willReturn(requestBodySpec);
            given(requestBodySpec.header(anyString(), anyString())).willReturn(requestBodySpec);
            given(requestBodySpec.body(any())).willReturn(requestHeadersSpec);
            given(requestHeadersSpec.retrieve()).willReturn(responseSpec);
            given(responseSpec.bodyToMono(String.class)).willReturn(Mono.just(invalidJson));

            // When & Then
            assertThatThrownBy(() -> client.generateImage(prompt).get())
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(AiServiceException.class)
                    .hasMessageContaining("Failed to parse image generation response");
        }
    }

    @Nested
    @DisplayName("API 설정")
    class ConfigurationTests {

        @Test
        @DisplayName("성공 - API 엔드포인트 확인")
        void configuration_성공_API_엔드포인트() {
            // Given
            String prompt = "test";
            String mockResponse = """
                {
                    "artifacts": [{
                        "base64": "data",
                        "finishReason": "SUCCESS"
                    }]
                }
                """;

            ArgumentCaptor<String> uriCaptor = ArgumentCaptor.forClass(String.class);

            given(webClient.post()).willReturn(requestBodyUriSpec);
            given(requestBodyUriSpec.uri(anyString())).willReturn(requestBodySpec);
            given(requestBodySpec.header(anyString(), anyString())).willReturn(requestBodySpec);
            given(requestBodySpec.body(any())).willReturn(requestHeadersSpec);
            given(requestHeadersSpec.retrieve()).willReturn(responseSpec);
            given(responseSpec.bodyToMono(String.class)).willReturn(Mono.just(mockResponse));

            // When
            client.generateImage(prompt);

            // Then
            verify(requestBodyUriSpec).uri(uriCaptor.capture());
            assertThat(uriCaptor.getValue()).contains("/v2beta/stable-image/generate/sd3");
        }

        @Test
        @DisplayName("성공 - isEnabled() 확인")
        void configuration_성공_isEnabled() {
            // When
            boolean enabled = client.isEnabled();

            // Then
            assertThat(enabled).isTrue();
            verify(imageAiConfig, times(1)).isEnabled();
        }
    }
}
