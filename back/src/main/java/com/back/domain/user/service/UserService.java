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
    public void signup(SignupRequest signupRequest) {
        // 사용자 회원가입 처리
        if (userRepository.findByLoginId(signupRequest.getLoginId()).isPresent()) {
            throw new ApiException(ErrorCode.LOGIN_ID_DUPLICATION);
        }
        if (userRepository.findByEmail(signupRequest.getEmail()).isPresent()) {
            throw new ApiException(ErrorCode.EMAIL_DUPLICATION);
        }

        User user = User.builder()
                .loginId(signupRequest.getLoginId())
                .email(signupRequest.getEmail())
                .password(passwordEncoder.encode(signupRequest.getPassword()))
                .nickname(signupRequest.getNickname())
                .birthdayAt(signupRequest.getBirthdayAt())
                .gender(signupRequest.getGender())
                .mbti(signupRequest.getMbti())
                .beliefs(signupRequest.getBeliefs())
                .authProvider(AuthProvider.LOCAL)
                .role(Role.USER)
                .build();

        userRepository.save(user);
    }

    @Transactional
    public User upsertOAuthUser(String email, String nickname, AuthProvider provider){
        Optional<User> found = userRepository.findByEmail(email);
        if(found.isPresent()){
            User u = found.get();
            if(u.getAuthProvider()==null) u.setAuthProvider(provider);
            if(u.getNickname()==null) u.setNickname(nickname);
            return u;
        }
        // 최초 소셜 로그인 시 필수값 기본 세팅
        User u = User.builder()
                .loginId(null)
                .email(email)
                .password(null)
                .nickname(nickname)
                .birthdayAt(LocalDateTime.now())
                .gender(Gender.N)
                .mbti(Mbti.INFP)
                .beliefs("자유")
                .role(Role.USER)
                .authProvider(provider)
                .build();
        return userRepository.save(u);
    }

    public User findByLoginId(String loginId) {
        // 로그인 ID로 사용자 정보 조회
        return userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
    }
}
