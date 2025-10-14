package com.back.global.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import redis.embedded.RedisServer;

import java.io.IOException;

/**
 * 로컬 및 테스트 환경 임베디드 Redis 설정
 * - test 프로필에서만 활성화
 */
@Configuration
@Profile("test")
public class EmbeddedRedisConfig {

    @Value("${spring.data.redis.port}")
    private int redisPort;

    private RedisServer redisServer;

    @PostConstruct
    public void startRedis() {
        try {
            redisServer = new RedisServer(redisPort);
            redisServer.start();
            System.out.println("========================================");
            System.out.println("Embedded Redis started on port " + redisPort);
            System.out.println("========================================");
        } catch (IOException e) {
            System.err.println("Embedded Redis start failed: " + e.getMessage());
        }
    }

    @PreDestroy
    public void stopRedis() {
        try {
            if (redisServer != null && redisServer.isActive()) {
                redisServer.stop();
                System.out.println("========================================");
                System.out.println("Embedded Redis stopped");
                System.out.println("========================================");
            }
        } catch (Exception e) {
            System.err.println("Error stopping embedded Redis: " + e.getMessage());
        }
    }
}