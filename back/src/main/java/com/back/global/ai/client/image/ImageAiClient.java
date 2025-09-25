package com.back.global.ai.client.image;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 이미지 생성 AI 클라이언트 인터페이스
 */
public interface ImageAiClient {
    /**
     * 프롬프트를 기반으로 이미지를 생성합니다.
     *
     * @param prompt 이미지 생성 프롬프트
     * @return 생성된 이미지 URL 또는 Base64 데이터
     */
    CompletableFuture<String> generateImage(String prompt);

    /**
     * 프롬프트와 추가 옵션을 사용하여 이미지를 생성합니다.
     *
     * @param prompt 이미지 생성 프롬프트
     * @param options 추가 옵션 (크기, 품질 등)
     * @return 생성된 이미지 URL 또는 Base64 데이터
     */
    CompletableFuture<String> generateImage(String prompt, Map<String, Object> options);

    /**
     * 이 클라이언트가 활성화되어 있는지 확인합니다.
     *
     * @return 활성화 여부
     */
    boolean isEnabled();
}
