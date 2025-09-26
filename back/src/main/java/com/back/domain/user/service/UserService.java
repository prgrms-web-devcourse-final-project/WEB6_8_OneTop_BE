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
 * 사용자 관련 비즈니스 로직을 처리하는 서비스입니다.
 * 회원가입(signup), 소셜 로그인 사용자 등록/갱신(upsertOAuthUser)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User signup(SignupRequest signupRequest) {
        // 사전 검증
        if (userRepository.existsByEmail(signupRequest.email())) {
            throw new ApiException(ErrorCode.EMAIL_DUPLICATION);
        }
        if (userRepository.existsByNickname(signupRequest.nickname())) {
            throw new ApiException(ErrorCode.NICKNAME_DUPLICATION); // 없으면 추가(아래 참고)
        }

        User user = User.builder()
                .email(signupRequest.email())
                .password(passwordEncoder.encode(signupRequest.password()))
                .username(signupRequest.username())
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
            if(user.getNickname()==null || user.getNickname().isBlank()){
                user.setNickname(safeUniqueNickname(nickname));
            }
            if(user.getUsername()==null || user.getUsername().isBlank()){
                user.setUsername(defaultUsernameFromEmail(email));
            }
            return user;
        }
        // 최초 소셜 로그인 시 필수값 기본 세팅
        String safeNick = safeUniqueNickname(nickname);
        String defaultUsername = defaultUsernameFromEmail(email);

        User user = User.builder()
                .email(email)
                .username(defaultUsername)
                .password(null)
                .nickname(safeNick)
                .birthdayAt(LocalDateTime.now())
                .role(Role.USER)
                .authProvider(provider)
                .build();
        return userRepository.save(user);
    }

    // 유니크 닉네임 생성
    private String safeUniqueNickname(String base) {
        if (base == null || base.isBlank()) base = "user";
        String nick = base;
        int i = 0;
        while (userRepository.existsByNickname(nick)) {
            i++;
            nick = base + i;
            if (nick.length() > 80) {
                nick = (base.length() > 75 ? base.substring(0, 75) : base) + i;
            }
        }
        return nick;
    }

    // 이메일로부터 username 생성
    private String defaultUsernameFromEmail(String email) {
        String local = (email != null && email.contains("@")) ? email.substring(0, email.indexOf('@')) : "user";
        String candidate = local.replaceAll("[^a-zA-Z0-9._-]", "");
        if (candidate.length() < 3) candidate = "user";
        if (candidate.length() > 30) candidate = candidate.substring(0, 30);
        return candidate;
    }
}
