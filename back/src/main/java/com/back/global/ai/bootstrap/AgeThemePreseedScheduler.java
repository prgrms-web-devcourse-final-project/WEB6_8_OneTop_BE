/*
 * [코드 흐름 요약]
 * - 최초 워밍업: 30초 간격 10회(≈5분) → 이후 5분 간격 전환(기존 유지).
 * - 각 틱마다 age 커서를 1씩 이동하며 ensureSeedForAgeAsync(age, MIN_PER_CATEGORY) 호출(기존 유지).
 * - 10만건 목표를 위해 MIN_PER_CATEGORY=120, 연령 구간을 3~120으로 확장.
 */
package com.back.global.ai.bootstrap;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Component
@Profile("test")
@RequiredArgsConstructor
public class AgeThemePreseedScheduler {

    private final AgeThemeSeeder seeder;

    // 무결성 검증
    private static final int WARMUP_RUNS = 10;       // 30초 간격으로 10회
    private static final int MIN_PER_CATEGORY = 120; // 카테고리당 최소 시드 개수(확대)
    private static final int MIN_AGE = 3;            // 순회 시작 연령(확장)
    private static final int MAX_AGE = 120;          // 순회 종료 연령(확장)

    private final AtomicInteger warmupCount = new AtomicInteger(0);
    private final AtomicInteger ageCursor   = new AtomicInteger(MIN_AGE);

    // next 노드 생성
    @Scheduled(initialDelay = 5_000, fixedRate = 30_000) // 첫 실행 5초 후, 30초 주기
    public void warmupPhase() {
        int n = warmupCount.get();
        if (n >= WARMUP_RUNS) return; // 무결성 검증
        tick();
        warmupCount.incrementAndGet();
    }

    // 무결성 검증
    @Scheduled(initialDelay = 5 * 60_000, fixedRate = 5 * 60_000) // 5분 주기
    public void steadyPhase() {
        if (warmupCount.get() < WARMUP_RUNS) return; // 워밍업 완료 전엔 대기
        tick();
    }

    // next 노드 생성
    private void tick() {
        int age = nextAge();
        seeder.ensureSeedForAgeAsync(age, MIN_PER_CATEGORY);
    }

    // 무결성 검증
    private int nextAge() {
        int cur = ageCursor.getAndUpdate(v -> (v >= MAX_AGE) ? MIN_AGE : (v + 1));
        return cur;
    }
}
