package com.back.util;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 프로덕션 Flyway 마이그레이션용 패스워드 인코딩 값을 생성합니다.
 * 테스트 실행 후 출력된 값을 V2__init_prod_essential.sql에 사용하세요.
 */
public class PasswordEncoderTest {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Test
    public void generateEncodedPasswords() {
        String adminPassword = "admin1234!";
        String encodedAdmin = passwordEncoder.encode(adminPassword);

        System.out.println("=================================================");
        System.out.println("Flyway 마이그레이션용 인코딩된 패스워드");
        System.out.println("=================================================");
        System.out.println("Admin Password (admin1234!):");
        System.out.println(encodedAdmin);
        System.out.println("=================================================");

        // 검증
        System.out.println("\n검증: " + passwordEncoder.matches(adminPassword, encodedAdmin));
    }
}
