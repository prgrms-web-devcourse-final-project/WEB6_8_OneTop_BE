package com.back.domain.user.service;

import com.back.domain.user.entity.*;
import com.back.domain.user.repository.UserRepository;
import com.back.domain.user.dto.SignupRequest;
import com.back.global.exception.ApiException;
import com.back.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 사용자 관련 비즈니스 로직을 처리하는 서비스.
 * 사용자 회원가입, 정보 조회 등의 기능을 제공합니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User signup(SignupRequest signupRequest) {
        // 사용자 회원가입 처리
        if (userRepository.findByEmail(signupRequest.email()).isPresent()) {
            throw new ApiException(ErrorCode.EMAIL_DUPLICATION);
        }

        User user = User.builder()
                .email(signupRequest.email())
                .password(passwordEncoder.encode(signupRequest.password()))
                .nickname(signupRequest.nickname())
                .birthdayAt(signupRequest.birthdayAt())
                .authProvider(AuthProvider.LOCAL)
                .role(Role.USER)
                .build();

        return userRepository.save(user);
    }

    @Transactional
    public User upsertOAuthUser(String email, String nickname, AuthProvider provider){
        Optional<User> found = userRepository.findByEmail(email);
        if(found.isPresent()){
            User user = found.get();
            if(user.getAuthProvider()==null) user.setAuthProvider(provider);
            if(user.getNickname()==null) user.setNickname(nickname);
            return user;
        }
        // 최초 소셜 로그인 시 필수값 기본 세팅
        User user = User.builder()
                .email(email)
                .password(null)
                .nickname(nickname)
                .birthdayAt(LocalDateTime.now())
                .role(Role.USER)
                .authProvider(provider)
                .build();
        return userRepository.save(user);
    }
}
