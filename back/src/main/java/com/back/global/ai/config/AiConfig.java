package com.back.global.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * AI 서비스 설정 클래스
 */
@Configuration
@ConfigurationProperties(prefix = "ai.gemini")
@Data
public class AiConfig {
    String apiKey;
    String baseUrl = "https://generativelanguage.googleapis.com";
    int timeoutSeconds = 30;
    int maxRetries = 3;
}
