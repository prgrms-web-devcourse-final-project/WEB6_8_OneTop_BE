package com.back.global.scheduler;

import com.back.domain.user.entity.Role;
import com.back.domain.user.entity.User;
import com.back.domain.user.repository.UserRepository;
import com.back.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 만료된 게스트 유저를 주기적으로 삭제하는 스케줄러
 * - Redis Session을 확인하여 실제로 세션이 없는 게스트만 삭제
 * - 매 10분마다 실행
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GuestCleanupScheduler {

    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final SessionRepository sessionRepository;

    @Scheduled(cron = "0 */30 * * * ?")
    @Transactional
    public void cleanupExpiredGuests() {
        log.info("=== 게스트 정리 작업 시작 ===");

        try {
            Set<String> sessionKeys = redisTemplate.keys("spring:session:sessions:*");
            Set<Long> activeGuestIds = new HashSet<>();

            if (sessionKeys != null && !sessionKeys.isEmpty()) {
                log.debug("현재 활성 세션 수: {}", sessionKeys.size());

                for (String key : sessionKeys) {
                    if (key.contains("expires")) continue; // expires 키는 스킵

                    String sessionId = key.replace("spring:session:sessions:", "");

                    try {
                        Session session = sessionRepository.findById(sessionId);
                        if (session == null) continue;

                        SecurityContext securityContext = session.getAttribute("SPRING_SECURITY_CONTEXT");
                        if (securityContext == null || securityContext.getAuthentication() == null) {
                            continue;
                        }

                        Object principal = securityContext.getAuthentication().getPrincipal();

                        if (principal instanceof CustomUserDetails) {
                            CustomUserDetails userDetails = (CustomUserDetails) principal;
                            User user = userDetails.getUser();

                            if (user.getRole() == Role.GUEST) {
                                activeGuestIds.add(user.getId());
                                log.debug("활성 게스트 세션: id={}", user.getId());
                            }
                        }
                    } catch (Exception e) {
                        log.warn("세션 읽기 실패: {} - {}", sessionId, e.getMessage());
                    }
                }
            }

            List<User> allGuests = userRepository.findByRole(Role.GUEST);

            if (allGuests.isEmpty()) {
                log.info("데이터베이스에 게스트 유저가 없습니다.");
                return;
            }

            log.debug("전체 게스트 유저 수: {}", allGuests.size());

            // 3. 세션이 없는 게스트 필터링
            List<User> expiredGuests = new ArrayList<>();

            for (User guest : allGuests) {
                if (!activeGuestIds.contains(guest.getId())) {
                    expiredGuests.add(guest);
                }
            }

            if (!expiredGuests.isEmpty()) {
                userRepository.deleteAll(expiredGuests);

                List<Long> deletedIds = expiredGuests.stream()
                        .map(User::getId)
                        .toList();

                log.info("{}명의 만료된 게스트 삭제 완료 - ID: {}", expiredGuests.size(), deletedIds);
            } else {
                log.info("삭제할 만료된 게스트가 없습니다.");
            }

            if (!activeGuestIds.isEmpty()) {
                log.info("활성 세션이 있는 게스트: {}명 - ID: {}", activeGuestIds.size(), activeGuestIds);
            }

            log.info("=== 게스트 정리 작업 완료 ===");

        } catch (Exception e) {
            log.error("게스트 정리 작업 중 오류 발생", e);
        }
    }
}