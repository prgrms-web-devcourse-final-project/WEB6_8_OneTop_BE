package com.back.global.initdata;

import com.back.domain.user.entity.Gender;
import com.back.domain.user.entity.Mbti;
import com.back.domain.user.entity.Role;
import com.back.domain.user.entity.User;
import com.back.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 애플리케이션 시작 시 초기 데이터를 생성하는 컴포넌트.
 * 개발 환경에서 필요한 기본 사용자(관리자, 일반 사용자)를 데이터베이스에 저장합니다.
 */
@Component
@RequiredArgsConstructor
public class InitData implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        // 애플리케이션 시작 시 초기 사용자 데이터 생성
        if (userRepository.findByEmail("admin@example.com").isEmpty()) {
            User admin = User.builder()
                    .email("admin@example.com")
                    .password(passwordEncoder.encode("admin1234!"))
                    .role(Role.ADMIN)
                    .nickname("관리자")
                    .birthdayAt(LocalDateTime.of(1990, 1, 1, 0, 0))
                    .gender(Gender.M)
                    .mbti(Mbti.INTJ)
                    .beliefs("합리주의")
                    .build();
            userRepository.save(admin);
        }

        if (userRepository.findByEmail("user1@example.com").isEmpty()) {
            User user1 = User.builder()
                    .email("user1@example.com")
                    .password(passwordEncoder.encode("user1234!"))
                    .role(Role.USER)
                    .nickname("사용자1")
                    .birthdayAt(LocalDateTime.of(1995, 5, 10, 0, 0))
                    .gender(Gender.F)
                    .mbti(Mbti.ENFP)
                    .beliefs("개인주의")
                    .build();
            userRepository.save(user1);
        }
    }
}