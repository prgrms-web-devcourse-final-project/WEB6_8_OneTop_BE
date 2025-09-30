package com.back.global.scheduler;

import com.back.domain.user.entity.Role;
import com.back.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 게스트 계정 자동 정리 스케줄러
 * 세션 리스너가 놓친 게스트 계정을 주기적으로 정리하는 안전망
 * 실행 시점: 매일 새벽 3시
 * 삭제 기준: 생성된 지 24시간이 지난 게스트 계정
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GuestCleanupScheduler {

    private final UserRepository userRepository;

    // 매일 새벽 3시에 실행
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanExpiredGuests() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        int deleted = userRepository.deleteByRoleAndCreatedDateBefore(Role.GUEST, cutoff);
        if (deleted > 0) {
            log.info("배치 작업으로 만료된 게스트 {}건 삭제", deleted);
        }
    }
}
