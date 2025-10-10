package com.back.global.config;

import com.back.global.ai.config.ImageAiConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * AWS S3 설정 (프로덕션 전용)
 * storageType="s3"일 때만 S3Client Bean을 생성합니다.
 *
 * 인증 방식:
 * - DefaultCredentialsProvider 자동 사용 (IAM Role 기반)
 * - EC2/ECS 환경에서 자동으로 credentials 획득
 *
 * S3Client 성능 설정:
 * - Connection Pool: 기본 설정 사용 (메모리 효율적)
 * - Retry 전략: 기본 3회 재시도
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ai.image", name = "storage-type", havingValue = "s3")
public class AwsS3Config {

    @Bean
    public S3Client s3Client(ImageAiConfig imageAiConfig) {
        if (imageAiConfig.getS3Region() == null || imageAiConfig.getS3Region().isEmpty()) {
            throw new IllegalStateException(
                "S3 region is required when storage-type is 's3'. " +
                "Please set ai.image.s3-region in application.yml"
            );
        }

        if (imageAiConfig.getS3BucketName() == null || imageAiConfig.getS3BucketName().isEmpty()) {
            throw new IllegalStateException(
                "S3 bucket name is required when storage-type is 's3'. " +
                "Please set ai.image.s3-bucket-name in application.yml"
            );
        }

        log.info("Initializing S3Client for bucket: {}, region: {}",
            imageAiConfig.getS3BucketName(), imageAiConfig.getS3Region());

        return S3Client.builder()
            .region(Region.of(imageAiConfig.getS3Region()))
            // DefaultCredentialsProvider 자동 사용:
            // - 로컬: 환경변수 또는 ~/.aws/credentials
            // - 프로덕션: IAM Role
            .build();
    }
}
