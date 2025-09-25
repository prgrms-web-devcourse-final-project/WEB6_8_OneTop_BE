package com.back.domain.user.service;

import com.back.domain.user.entity.*;
import com.back.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 게스트 계정을 DB에 저장합니다.
 * 고유한 loginId/email을 생성하고 ROLE_GUEST로 저장합니다.
 */
@Service
@RequiredArgsConstructor
public class GuestService {
    private final UserRepository userRepository;

    @Transactional
    public User createAndSaveGuest(){
        String guestLoginId = "guest_" + UUID.randomUUID().toString().substring(0, 8);
        String guestEmail = guestLoginId + "@example.com";


        User guest = User.builder()
                .email(guestEmail)
                .password(null) // 게스트 비밀번호 없음(추후 전환 시 설정)
                .nickname("게스트_" + UUID.randomUUID().toString().substring(0, 4))
                .birthdayAt(LocalDateTime.now())
                .gender(Gender.N)
                .mbti(Mbti.INFP)
                .beliefs("자유")
                .role(Role.GUEST)
                .authProvider(AuthProvider.LOCAL)
                .build();
        return userRepository.save(guest);
    }
}
