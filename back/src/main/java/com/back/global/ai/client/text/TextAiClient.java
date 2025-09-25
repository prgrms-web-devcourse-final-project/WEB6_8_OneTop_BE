package com.back.global.ai.client.text;

import com.back.global.ai.dto.AiRequest;

import java.util.concurrent.CompletableFuture;

/**
 * 텍스트 생성 AI 클라이언트 인터페이스
 */
public interface TextAiClient {
    /**
     * 주어진 프롬프트로 텍스트를 생성합니다.
     *
     * @param prompt 생성할 텍스트의 프롬프트
     * @return 생성된 텍스트
     */
    CompletableFuture<String> generateText(String prompt);

    /**
     * 파라미터가 포함된 프롬프트로 텍스트를 생성합니다.
     *
     * @param aiRequest AI 요청 객체
     * @return 생성된 텍스트
     */
    CompletableFuture<String> generateText(AiRequest aiRequest);
}
