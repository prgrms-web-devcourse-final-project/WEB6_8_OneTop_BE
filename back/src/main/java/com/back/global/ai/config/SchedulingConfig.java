/*
 * [코드 흐름 요약]
 * - Spring @Scheduled 기능 전역 활성화.
 * - 프로필 제약 없음(스케줄러 자체는 @Profile("!test")로 제한).
 */
package com.back.global.ai.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class SchedulingConfig {
    // next 노드 생성
    // 빈 없음: EnableScheduling 활성화 전용
    // 무결성 검증
}
