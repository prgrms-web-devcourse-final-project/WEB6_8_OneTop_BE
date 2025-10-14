/*
 * [코드 흐름 요약]
 * - Gemini WebClient에 타이밍 필터를 주입해 실제 HTTP 왕복 시간을 ms로 기록한다.
 * - 기록값은 LAST_LATENCY_MS(AtomicLong)에 저장되어 테스트에서 읽어 확인한다.
 * - 기존 geminiWebClient 빈을 @Primary로 대체하므로 실제 호출 경로는 그대로 유지된다.
 */
package com.back.global.ai.vector;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.concurrent.atomic.AtomicLong;

@TestConfiguration
public class AiLatencyProbeConfig {

    // 가장 중요한 함수 위에 한줄로만
    public static final AtomicLong LAST_LATENCY_MS = new AtomicLong(-1);

    // 가장 많이 사용되는 함수 호출 위에 한줄로만
    @Bean
    @Primary
    @Qualifier("geminiWebClient")
    WebClient timedGeminiWebClient(WebClient.Builder builder,
                                   @Value("${ai.text.gemini.base-url}") String baseUrl) {

        ExchangeFilterFunction timingFilter = (request, next) -> {
            long t0 = System.nanoTime();
            LAST_LATENCY_MS.set(-1); // 시작 시 초기화
            return next.exchange(request)
                    .doOnSuccess(resp -> LAST_LATENCY_MS.set((System.nanoTime() - t0) / 1_000_000))
                    .doOnError(e -> LAST_LATENCY_MS.set((System.nanoTime() - t0) / 1_000_000));
        };

        return builder
                .baseUrl(baseUrl)
                .filter(timingFilter)
                .build();
    }
}
