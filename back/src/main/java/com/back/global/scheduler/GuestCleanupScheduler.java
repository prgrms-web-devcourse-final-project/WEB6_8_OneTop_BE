package com.back.global.scheduler;

import com.back.domain.node.entity.BaseLine;
import com.back.domain.node.repository.BaseLineRepository;
import com.back.domain.node.service.BaseLineService;
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

import java.util.List;
import java.util.Map;

/**
 * 게스트 유저 정리 보조 Scheduler
 * - 세션 리스너에서 처리하지 못하고 남은 게스트가 있다면 정리
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GuestCleanupScheduler {

    private final UserRepository userRepository;
    private final BaseLineRepository baseLineRepository;
    private final BaseLineService baseLineService;
    private final FindByIndexNameSessionRepository<? extends Session> sessionIndexRepo;

    @Scheduled(cron = "0 0 17 * * ?")  // 매일 오후 5시
    @Transactional
    public void cleanupExpiredGuests() {
        log.info("=== 게스트 정리 작업 시작 ===");

        List<User> allGuests = userRepository.findByRole(Role.GUEST);
        if (allGuests.isEmpty()) {
            log.info("게스트 유저가 없습니다.");
            return;
        }

        int deletedUsers = 0;

        for (User guest : allGuests) {
            String principal = guest.getEmail();

            Map<String, ? extends Session> sessions =
                    sessionIndexRepo.findByIndexNameAndIndexValue(
                            FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,
                            principal
                    );

            boolean hasActive = sessions != null && !sessions.isEmpty();
            if (hasActive) { continue; }

            Long userId = guest.getId();

            try {
                List<Long> baseLineIds = baseLineRepository
                        .findByUser_IdOrderByIdDesc(userId)
                        .stream()
                        .map(BaseLine::getId)
                        .toList();

                for (Long baseLineId : baseLineIds) {
                    try {
                        baseLineService.deleteBaseLineDeep(userId, baseLineId);
                    } catch (Exception ex) {
                        log.error("[GuestCleanupScheduler] baseline({}) deep 삭제 실패 - 계속 진행", baseLineId, ex);
                    }
                }

                userRepository.delete(guest);
                deletedUsers++;

                log.info("[GuestCleanupScheduler] 게스트 및 소유 리소스 삭제 완료: email={}, userId={}",
                        principal, userId);

            } catch (Exception e) {
                log.error("[GuestCleanupScheduler] 게스트(userId={}, email={}) 삭제 처리 중 오류 - 다음 유저로 계속",
                        userId, principal, e);
            }
        }

        if (deletedUsers > 0) {
            log.info("만료 게스트 {}명 삭제 완료", deletedUsers);
        } else {
            log.info("삭제할 만료 게스트가 없습니다.");
        }

        log.info("=== 게스트 정리 작업 완료 ===");
    }
}