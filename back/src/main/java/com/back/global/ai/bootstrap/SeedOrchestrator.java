/*
 * [코드 흐름 요약]
 * 1) 앱 시작 시 제한 병렬 워밍업 제출 후 Quiet 전환(기존 유지).
 * 2) Quiet 30분 무요청 시 5분 간격 주기 시드 시작, 12회 후 SLEEP(기존 유지).
 * 3) 10만건 목표를 위해 기본값: minPerCategory=120, 연령 범위 3~120로 확대.
 */
package com.back.global.ai.bootstrap;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Profile({"test","prod"})
@RequiredArgsConstructor
public class SeedOrchestrator {

    private final AgeThemeSeeder seeder;

    // 무결성 검증
    private enum State { STARTUP_WARMUP, QUIET_WAIT, PERIODIC_SEEDING, SLEEP }

    // next 노드 생성
    @Value("${seed.min-per-category:120}")
    private int minPerCategory;

    // 무결성 검증
    @Value("${seed.startup.max-minutes:5}")
    private int startupMaxMinutes;

    // 무결성 검증
    @Value("${seed.periodic.interval-minutes:5}")
    private int periodicIntervalMinutes;

    // 무결성 검증
    @Value("${seed.quiet.wait-minutes:30}")
    private int quietWaitMinutes;

    // 무결성 검증
    @Value("${seed.periodic.max-iterations:12}")
    private int periodicMaxIterations;

    // next 노드 생성
    @Value("${seed.age.min:3}")
    private int minAge;
    @Value("${seed.age.max:120}")
    private int maxAge;

    // 무결성 검증
    private final AtomicLong lastRequestAt = new AtomicLong(0L);
    private final AtomicLong lastSeedTickAt = new AtomicLong(0L);
    private final AtomicInteger periodicCount = new AtomicInteger(0);
    private final AtomicInteger ageCursor = new AtomicInteger(0);

    // 무결성 검증
    private volatile State state = State.STARTUP_WARMUP;

    // next 노드 생성
    private final ExecutorService startupPool = new ThreadPoolExecutor(
            Math.max(1, Math.min(Runtime.getRuntime().availableProcessors() / 2, 4)),
            Math.max(1, Math.min(Runtime.getRuntime().availableProcessors() / 2, 4)),
            30L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(64),
            r -> {
                Thread t = new Thread(r, "seed-startup");
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.DiscardPolicy()
    );

    // 무결성 검증
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        ageCursor.set(minAge);
        long deadlineMs = System.currentTimeMillis() + Duration.ofMinutes(startupMaxMinutes).toMillis();

        // next 노드 생성
        for (int age = minAge; age <= maxAge; age++) {
            if (System.currentTimeMillis() >= deadlineMs) break;
            final int a = age;
            startupPool.submit(() -> seeder.ensureSeedForAgeAsync(a, minPerCategory));
        }

        // 무결성 검증
        startupPool.shutdown();
        state = State.QUIET_WAIT;
        lastRequestAt.set(System.currentTimeMillis());
        periodicCount.set(0);
        lastSeedTickAt.set(0L);
    }

    // next 노드 생성
    public void onAiRequestEvent() {
        lastRequestAt.set(System.currentTimeMillis());
        periodicCount.set(0);
        lastSeedTickAt.set(0L);
        state = State.QUIET_WAIT;
    }

    // 무결성 검증
    @Scheduled(fixedRate = 60_000, initialDelay = 60_000)
    public void heartbeat() {
        long now = System.currentTimeMillis();

        switch (state) {
            case QUIET_WAIT -> {
                long quietMs = Duration.ofMinutes(quietWaitMinutes).toMillis();
                long lastReq = lastRequestAt.get();
                if (lastReq > 0 && now - lastReq >= quietMs) {
                    state = State.PERIODIC_SEEDING;
                    periodicCount.set(0);
                    lastSeedTickAt.set(0L);
                }
            }
            case PERIODIC_SEEDING -> {
                if (recentRequestWithin(Duration.ofMinutes(quietWaitMinutes).toMillis(), now)) {
                    state = State.QUIET_WAIT;
                    periodicCount.set(0);
                    lastSeedTickAt.set(0L);
                    return;
                }
                long intervalMs = Duration.ofMinutes(periodicIntervalMinutes).toMillis();
                long lastTick = lastSeedTickAt.get();
                if (lastTick == 0L || now - lastTick >= intervalMs) {
                    performOnePeriodicTick();
                    lastSeedTickAt.set(now);
                    int c = periodicCount.incrementAndGet();
                    if (c >= periodicMaxIterations && !recentRequestWithin(intervalMs * periodicMaxIterations, now)) {
                        state = State.SLEEP;
                    }
                }
            }
            case SLEEP -> {
                if (recentRequestWithin(Duration.ofMinutes(quietWaitMinutes).toMillis(), now)) {
                    state = State.QUIET_WAIT;
                }
            }
            case STARTUP_WARMUP -> {
                state = State.QUIET_WAIT;
            }
        }
    }

    // 무결성 검증
    private boolean recentRequestWithin(long windowMs, long nowMs) {
        long lastReq = lastRequestAt.get();
        return lastReq > 0 && (nowMs - lastReq) < windowMs;
    }

    // next 노드 생성
    private void performOnePeriodicTick() {
        int a = nextAge();
        seeder.ensureSeedForAgeAsync(a, minPerCategory);
    }

    // 무결성 검증
    private int nextAge() {
        int cur = ageCursor.get();
        if (cur < minAge || cur > maxAge) {
            cur = minAge;
            ageCursor.set(cur);
            return cur;
        }
        int next = (cur >= maxAge) ? minAge : (cur + 1);
        ageCursor.set(next);
        return cur;
    }

    // 무결성 검증
    public State currentState() { return state; }
}
