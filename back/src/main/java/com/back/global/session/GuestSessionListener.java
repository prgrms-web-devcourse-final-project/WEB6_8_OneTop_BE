package com.back.global.session;

import com.back.domain.user.entity.Role;
import com.back.domain.user.repository.UserRepository;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
