package com.back.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 비동기 처리를 위한 ThreadPool 설정
 * AI 호출 등 장시간 소요되는 작업을 비동기로 처리하기 위한 스레드풀 구성
 */
@Configuration
public class AsyncConfig {

    /**
     * AI 서비스 비동기 처리용 ThreadPoolTaskExecutor
     *
     * @return 설정된 Executor
     */
    @Bean(name = "aiTaskExecutor")
    public Executor aiTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 코어 스레드 수: AWS Small 티어에 맞춘 최소 설정
        executor.setCorePoolSize(2);

        // 최대 스레드 수: CPU 코어 수 고려 (1-2 vCPU)
        executor.setMaxPoolSize(4);

        // 큐 용량: 대기 큐를 늘려 메모리로 버퍼링 (메모리 2GB 고려)
        executor.setQueueCapacity(100);

        // 스레드 이름 접두사
        executor.setThreadNamePrefix("AI-Async-");

        // 종료 대기: 애플리케이션 종료 시 실행 중인 작업 완료 대기
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        executor.initialize();
        return executor;
    }
}
