package com.back.global.scheduler;

import com.back.domain.user.entity.Role;
import com.back.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class GuestCleanupScheduler {

    private final UserRepository userRepository;

    // 5분마다 실행 (밀리초 단위)
    @Scheduled(fixedRate = 300000)
    @Transactional
    public void cleanExpiredGuests() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(10);
        int deleted = userRepository.deleteByRoleAndCreatedDateBefore(Role.GUEST, cutoff);
        if (deleted > 0) {
            log.info("배치 작업으로 만료된 게스트 {}건 삭제", deleted);
        }
    }
}
