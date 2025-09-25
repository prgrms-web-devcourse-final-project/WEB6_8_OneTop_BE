package com.back.global.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

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
}
