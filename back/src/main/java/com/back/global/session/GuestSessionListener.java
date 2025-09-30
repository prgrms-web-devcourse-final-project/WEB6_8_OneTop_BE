package com.back.global.session;

import com.back.domain.user.entity.Role;
import com.back.domain.user.repository.UserRepository;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게스트 세션 종료 리스너
 * HTTP 세션이 종료될 때 해당 게스트 계정을 즉시 삭제
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GuestSessionListener implements HttpSessionListener {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public void sessionDestroyed(HttpSessionEvent se) {
        Object guestIdObj = se.getSession().getAttribute("guestId");

        if (guestIdObj instanceof Long guestId) {
            userRepository.findById(guestId).ifPresent(user -> {
                if (user.getRole() == Role.GUEST) {
                    userRepository.delete(user);
                    log.info("세션 만료로 게스트 계정 삭제됨: {}", guestId);
                }
            });
        }
    }
}
