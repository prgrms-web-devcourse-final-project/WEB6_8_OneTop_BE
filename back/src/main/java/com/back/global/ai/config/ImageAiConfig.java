package com.back.global.ai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 이미지 생성 AI 설정 프로퍼티
 * application.yml의 ai.image 설정을 바인딩합니다.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "ai.image")
public class ImageAiConfig {

    /**
     * 이미지 AI 기능 활성화 여부
     */
    private boolean enabled = false;

    /**
     * 이미지 AI 제공자 (예: stable-diffusion)
     */
    private String provider = "stable-diffusion";

    /**
     * Stability AI API 키
     */
    private String apiKey;

    /**
     * Stability AI API 베이스 URL
     */
    private String baseUrl = "https://api.stability.ai";

    /**
     * API 호출 타임아웃 (초)
     */
    private int timeoutSeconds = 60;

    /**
     * 재시도 최대 횟수
     */
    private int maxRetries = 3;

    /**
     * 이미지 저장 방식 (s3, local 등)
     */
    private String storageType = "s3";

    /**
     * AWS S3 버킷 이름 (storageType이 s3인 경우)
     */
    private String s3BucketName;

    /**
     * AWS S3 리전 (storageType이 s3인 경우)
     */
    private String s3Region;
}
