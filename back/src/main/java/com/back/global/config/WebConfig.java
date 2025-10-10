package com.back.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.config.PageableHandlerMethodArgumentResolverCustomizer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 웹 관련 설정을 정의하는 구성 클래스.
 * - 페이징 설정
 * - 정적 리소스 매핑 (로컬 이미지 서빙)
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${ai.image.storage-type:local}")
    private String storageType;

    @Value("${ai.image.local-storage-path:./uploads/images}")
    private String localStoragePath;

    // 스프링의 페이지 요청을 1부터 시작하도록 설정
    // 최대 페이지 크기 및 기본 페이지 설정
    @Bean
    public PageableHandlerMethodArgumentResolverCustomizer customizer() {
        return pageableResolver -> {
            pageableResolver.setOneIndexedParameters(true); // page 요청이 1로 오면 0으로 인식
            pageableResolver.setMaxPageSize(50);
            pageableResolver.setFallbackPageable(PageRequest.of(0, 5));
        };
    }

    /**
     * 정적 리소스 핸들러 설정
     * 로컬 개발 환경에서만 /images/** 경로를 로컬 파일 시스템에 매핑
     * S3 환경에서는 S3 URL을 직접 사용하므로 이 설정 불필요
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        if ("local".equals(storageType)) {
            registry.addResourceHandler("/images/**")
                    .addResourceLocations("file:" + localStoragePath + "/")
                    .setCachePeriod(3600); // 1시간 캐싱
        }
    }
}
