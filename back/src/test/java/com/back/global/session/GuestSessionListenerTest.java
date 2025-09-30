package com.back.global.session;

import com.back.domain.node.entity.BaseLine;
import com.back.domain.node.repository.BaseLineRepository;
import com.back.domain.user.entity.Role;
import com.back.domain.user.entity.User;
import com.back.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpSessionEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GuestSessionListenerTest {

    @Autowired
    private GuestSessionListener listener;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BaseLineRepository baseLineRepository;

    @PersistenceContext
    private EntityManager em;

    @Test
    @DisplayName("성공 - 세션 만료 시 게스트와 연관 엔티티가 함께 삭제된다")
    void t1() {
        User guest = userRepository.save(User.builder()
                .email("guest_delete@example.com")
                .username("guest_delete")
                .nickname("삭제될 게스트")
                .birthdayAt(LocalDateTime.now())
                .role(Role.GUEST)
                .build());

        // BaseLine 생성
        BaseLine baseLine = BaseLine.builder()
                .user(guest)
                .title("게스트의 시나리오")
                .build();
        guest.getBaseLines().add(baseLine);
        baseLineRepository.save(baseLine);

        Long guestId = guest.getId();
        Long baselineId = baseLine.getId();

        // 세션에 guestId 저장
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("guestId", guestId);
        HttpSessionEvent event = new HttpSessionEvent(session);

        // 세션 종료 이벤트 발생
        listener.sessionDestroyed(event);

        assertThat(userRepository.findById(guestId)).isEmpty();
        assertThat(baseLineRepository.findById(baselineId)).isEmpty();
    }
}