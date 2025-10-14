package com.back.global.scheduler;

import com.back.domain.user.entity.Role;
import com.back.domain.user.entity.User;
import com.back.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 게스트 유저 정리 보조 scheduler
 * - 세션 리스너에서 처리하지 못하고 남은 게스트가 있다면 삭제
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GuestCleanupScheduler {

    private final UserRepository userRepository;
    private final FindByIndexNameSessionRepository<? extends Session> sessionIndexRepo;

    @Scheduled(cron = "0 0 17 * * ?")  // 매일 오후 5시
    @Transactional
    public void cleanupExpiredGuests() {
        log.info("=== 게스트 정리 작업 시작 (index 기반) ===");

        List<User> allGuests = userRepository.findByRole(Role.GUEST);
        if (allGuests.isEmpty()) {
            log.info("게스트 유저가 없습니다.");
            return;
        }

        List<User> expiredGuests = new ArrayList<>();

        for (User guest : allGuests) {
            String principal = guest.getEmail();

            Map<String, ? extends Session> sessions =
                    sessionIndexRepo.findByIndexNameAndIndexValue(
                            FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,
                            principal
                    );

            boolean hasActive = sessions != null && !sessions.isEmpty();
            if (!hasActive) expiredGuests.add(guest);
        }

        if (!expiredGuests.isEmpty()) {
            userRepository.deleteAllInBatch(expiredGuests);
            log.info("만료 게스트 {}명 삭제 완료 - IDs={}",
                    expiredGuests.size(),
                    expiredGuests.stream().map(User::getId).toList());
        } else {
            log.info("삭제할 만료 게스트가 없습니다.");
        }

        log.info("=== 게스트 정리 작업 완료 ===");
    }
}