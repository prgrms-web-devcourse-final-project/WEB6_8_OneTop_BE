package com.back.domain.user.service;

import com.back.domain.user.entity.*;
import com.back.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 게스트 사용자 계정을 생성하고 DB에 저장하는 서비스 클래스입니다.
 * 고유한 email을 생성하고 GUEST로 저장합니다.
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
                .username(guestLoginId)
                .password(null)
                .nickname("게스트_" + UUID.randomUUID().toString().substring(0, 4))
                .birthdayAt(LocalDateTime.now())
                .role(Role.GUEST)
                .authProvider(AuthProvider.GUEST)
                .build();
        return userRepository.save(guest);
    }
}
