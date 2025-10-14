/**
 * DvcsBackfillRunner (근본 개선판)
 * - main 브랜치/루트 커밋 보장
 * - BaseNode.currentVersion 미설정 시 Atom/Version 생성 후 연결
 * - DecisionLine.baseBranch 미설정 시 main 연결
 * - 마지막으로 **각 BaseLine의 커밋 체인에서 ageYear별 초기 패치 부재 시 루트 커밋에 생성**
 */
package com.back.domain.node.migration;

import com.back.domain.node.service.DvcsBackfillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"local","dev","prod"})
@ConditionalOnProperty(name = "dvcs.backfill.enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
@Slf4j
public class DvcsBackfillRunner implements ApplicationRunner {

    private final DvcsBackfillService service;

    @Override
    public void run(ApplicationArguments args) {
        log.info("[DVCS-BACKFILL] start");
        service.backfill();
        log.info("[DVCS-BACKFILL] done");
    }
}