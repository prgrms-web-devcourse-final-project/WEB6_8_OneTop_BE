package com.back.domain.session.service;

import com.back.domain.session.repository.SessionRepository;
import com.back.domain.user.entity.Gender;
import com.back.domain.user.entity.Mbti;
import com.back.domain.user.entity.Role;
import com.back.domain.user.entity.User;
import com.back.domain.user.repository.UserRepository;
import com.back.global.config.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;

/**
 * 세션 관련 비즈니스 로직을 처리하는 서비스.
 * 게스트 사용자 인증 및 세션 관리를 담당합니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SessionService {

    private final SessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public Authentication authenticateGuest() {
        // 게스트 유저를 생성하고 Authentication 객체를 반환
        String guestLoginId = "guest_" + UUID.randomUUID().toString().substring(0, 8);
        String guestEmail = guestLoginId + "@example.com";
        String guestPassword = UUID.randomUUID().toString();

        User guestUser = User.builder()
                .loginId(guestLoginId)
                .email(guestEmail)
                .password(guestPassword)
                .nickname("게스트_" + UUID.randomUUID().toString().substring(0, 4))
                .birthdayAt(LocalDateTime.now())
                .gender(Gender.N)
                .mbti(Mbti.INFP)
                .beliefs("자유")
                .role(Role.GUEST)
                .build();

        CustomUserDetails guestUserDetails = new CustomUserDetails(guestUser);
        return new UsernamePasswordAuthenticationToken(
                guestUserDetails,
                guestUser.getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + guestUser.getRole().name()))
        );
    }
}