package com.back.global.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 텍스트 생성 AI 서비스 설정 클래스
 */
@Configuration
@ConfigurationProperties(prefix = "ai.text.gemini")
@Data
public class TextAiConfig {
    String apiKey;
    String baseUrl = "https://generativelanguage.googleapis.com";
    String model = "gemini-2.5-pro"; // 추후 변경 가능
    int timeoutSeconds = 30;
    int maxRetries = 3;

    /**
     * Gemini API 전용 WebClient Bean 생성
     */
    @Bean("geminiWebClient")
    public WebClient geminiWebClient() {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Content-Type", "application/json")
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024))
                .build();
    }
}
