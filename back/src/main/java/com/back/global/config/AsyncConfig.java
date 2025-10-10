package com.back.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 비동기 처리를 위한 ThreadPool 설정
 * AI 호출 등 장시간 소요되는 작업을 비동기로 처리하기 위한 스레드풀 구성
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Value("${spring.task.execution.pool.core-size}")
    private int corePoolSize;

    @Value("${spring.task.execution.pool.max-size}")
    private int maxPoolSize;

    @Value("${spring.task.execution.pool.queue-capacity}")
    private int queueCapacity;

    @Value("${spring.task.execution.pool.await-termination-seconds}")
    private int awaitTerminationSeconds;

    @Value("${spring.task.execution.thread-name-prefix}")
    private String threadNamePrefix;

    /**
     * AI 서비스 비동기 처리용 ThreadPoolTaskExecutor
     * application.yml의 spring.task.execution 설정을 사용합니다.
     *
     * @return 설정된 Executor
     */
    @Bean(name = "aiTaskExecutor")
    public Executor aiTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // application.yml에서 주입받은 설정 사용
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(threadNamePrefix);

        // 종료 대기: 애플리케이션 종료 시 실행 중인 작업 완료 대기
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(awaitTerminationSeconds);

        executor.initialize();
        return executor;
    }
}
