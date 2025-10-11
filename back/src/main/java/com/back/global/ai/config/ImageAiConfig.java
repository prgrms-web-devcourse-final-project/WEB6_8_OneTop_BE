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

    // 이미지 AI 기능 활성화 여부
    private boolean enabled = false;

    // 이미지 AI 제공자
    private String provider = "stable-diffusion";

    private String apiKey;

    private String baseUrl = "https://api.stability.ai";

    private int timeoutSeconds = 60;

    private int maxRetries = 3;

    private int retryDelaySeconds = 2; // 재시도 간격 (초)

    // 이미지 저장 방식 (s3, local 등)
    private String storageType = "local";

    // AWS S3 버킷 이름 (storageType이 s3인 경우)
    private String s3BucketName;

    // AWS S3 리전 (storageType이 s3인 경우)
    private String s3Region;

    // 로컬 파일 저장 경로 (storageType="local"인 경우 사용)
    // 기본값: "./uploads/images"
    private String localStoragePath = "./uploads/images";

    // 기본값: "http://localhost:8080/images"
    private String localBaseUrl = "http://localhost:8080/images";

    public boolean isS3Enabled() {
        return "s3".equalsIgnoreCase(storageType);
    }

    public boolean isLocalEnabled() {
        return "local".equalsIgnoreCase(storageType);
    }
}
