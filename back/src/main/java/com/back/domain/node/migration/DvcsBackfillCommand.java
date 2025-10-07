// [BATCH] DvcsBackfillCommand (수동 트리거 전용, 트랜잭션은 서비스에 있음)
package com.back.domain.node.migration;

import com.back.domain.node.service.DvcsBackfillService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.locks.ReentrantLock;

@RestController
@RequestMapping("/internal/migration")
@Profile({"local","dev"})
@ConditionalOnProperty(name = "dvcs.backfill.http-enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
public class DvcsBackfillCommand {

    private final DvcsBackfillService service;
    private final ReentrantLock lock = new ReentrantLock();

    @PostMapping("/dvcs/backfill")
    public ResponseEntity<String> trigger() {
        if (!lock.tryLock()) {
            return ResponseEntity.status(409).body("already running");
        }
        try {
            service.backfill();
            return ResponseEntity.ok("OK");
        } finally {
            lock.unlock();
        }
    }
}
