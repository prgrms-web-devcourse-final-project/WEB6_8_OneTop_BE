package com.back.global.ai.client.image;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 이미지 AI 기능이 비활성화되었을 때 사용되는 NoOp 구현체.
 * 실제 이미지를 생성하지 않고 placeholder URL을 반환합니다.
 *
 * 활성화 조건:
 * - ai.image.enabled=false인 경우
 * - STABILITY_API_KEY가 설정되지 않은 경우
 * - S3 연결이 불가능한 프로덕션 환경
 */
@Slf4j
@Component
@ConditionalOnMissingBean(ImageAiClient.class)
public class NoOpImageAiClient implements ImageAiClient {

    public NoOpImageAiClient() {
        log.info("NoOpImageAiClient initialized - Image generation is disabled");
    }

    @Override
    public CompletableFuture<String> generateImage(String prompt) {
        log.debug("Image generation disabled. Returning placeholder for prompt: {}", prompt);
        return CompletableFuture.completedFuture("placeholder-image-url");
    }

    @Override
    public CompletableFuture<String> generateImage(String prompt, Map<String, Object> options) {
        log.debug("Image generation disabled. Returning placeholder for prompt: {}", prompt);
        return CompletableFuture.completedFuture("placeholder-image-url");
    }

    @Override
    public boolean isEnabled() {
        return false;
    }
}
