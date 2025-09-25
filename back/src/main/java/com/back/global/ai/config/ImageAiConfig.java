package com.back.global.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 이미지 생성 AI 서비스 설정 클래스
 */
@Configuration
@ConfigurationProperties(prefix = "ai.image")
@Data
public class ImageAiConfig {
    private boolean enabled = false;
    private String provider = "placeholder";
    private int timeoutSeconds = 60;
    private int maxRetries = 3;

    // TODO: 추후 설정 추가 예정
}