package com.back.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.config.PageableHandlerMethodArgumentResolverCustomizer;

/**
 * 웹 관련 설정을 정의하는 구성 클래스.
 */
@Configuration
public class WebConfig {

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
}
