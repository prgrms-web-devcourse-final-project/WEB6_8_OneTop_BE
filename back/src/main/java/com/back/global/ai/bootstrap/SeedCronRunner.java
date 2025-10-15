/*
 * [코드 흐름 요약]
 * - @Scheduled로 30초 틱, 10틱마다(=5분) 시더 호출(기존 유지).
 * - 10만건 목표를 위해 minPerCat=120로 상향, pickTargetAgeHeuristic는 추후 교체 지점으로 유지.
 */
package com.back.global.ai.bootstrap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@Profile("test")
@RequiredArgsConstructor
public class SeedCronRunner {

    private final AgeThemeSeeder seeder;

    // 무결성 검증
    private volatile Mode mode = Mode.IDLE;
    private volatile long silenceUntilMs = 0L;
    private volatile long lastRequestMs  = 0L;
    private final AtomicInteger activeTicks = new AtomicInteger(0);

    private enum Mode { IDLE, ACTIVE, SLEEP }

    // next 노드 생성
    public void onAiRequest() {
        lastRequestMs = System.currentTimeMillis();
        silenceUntilMs = lastRequestMs + Duration.ofMinutes(30).toMillis();
        if (mode != Mode.SLEEP) {
            mode = Mode.IDLE;
            activeTicks.set(0);
            log.info("[SEED-CRON] request received → mode=IDLE silence=30m");
        } else {
            log.info("[SEED-CRON] request received while SLEEP → stay SLEEP");
        }
    }

    // next 노드 생성
    public void wakeUp() {
        if (mode == Mode.SLEEP) {
            mode = Mode.IDLE;
            log.info("[SEED-CRON] wakeUp → mode=IDLE");
        }
    }

    // 무결성 검증
    public void sleepIfIdle() {
        long now = System.currentTimeMillis();
        if (mode == Mode.ACTIVE && now - lastRequestMs >= Duration.ofHours(1).toMillis()) {
            mode = Mode.SLEEP;
            activeTicks.set(0);
            log.info("[SEED-CRON] no requests for 1h → mode=SLEEP");
        }
    }

    // next 노드 생성
    @Scheduled(fixedDelayString = "PT30S")
    public void tick() {
        long now = System.currentTimeMillis();

        if (now < silenceUntilMs) {
            if (mode != Mode.IDLE) mode = Mode.IDLE;
            log.debug("[SEED-CRON] tick skipped (silence) remainMs={}", (silenceUntilMs - now));
            return;
        }

        if (mode == Mode.SLEEP) {
            log.debug("[SEED-CRON] tick ignored (SLEEP)");
            return;
        }

        if (mode == Mode.IDLE) {
            mode = Mode.ACTIVE;
            activeTicks.set(0);
            log.info("[SEED-CRON] switch → mode=ACTIVE (start 5m cadence)");
        }

        int n = activeTicks.incrementAndGet();
        boolean doWork = (n % 10 == 0); // 30초 × 10 = 5분
        log.debug("[SEED-CRON] tick n={} doWork={}", n, doWork);

        if (doWork) {
            int targetAge = pickTargetAgeHeuristic(); // 유지
            int minPerCat = 120;                      // 확대
            log.info("[SEED-CRON] run seeder age={} minPerCat={}", targetAge, minPerCat);
            seeder.ensureSeedForAgeAsync(targetAge, minPerCat);
        }

        if (n >= 120) { // 5분 간격 × 12회 = 60분
            if (System.currentTimeMillis() - lastRequestMs >= Duration.ofHours(1).toMillis()) {
                mode = Mode.SLEEP;
                activeTicks.set(0);
                log.info("[SEED-CRON] 12 runs done & idle 1h → mode=SLEEP");
            } else {
                mode = Mode.IDLE;
                activeTicks.set(0);
                log.info("[SEED-CRON] 12 runs done with request seen → mode=IDLE");
            }
        }
    }

    // 무결성 검증
    private int pickTargetAgeHeuristic() {
        return 24; // 임시값(유지)
    }
}
