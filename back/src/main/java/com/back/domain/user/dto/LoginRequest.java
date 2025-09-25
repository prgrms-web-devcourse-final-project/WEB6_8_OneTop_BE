package com.back.domain.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 사용자 로그인 요청 시 필요한 정보를 담는 DTO 클래스.
 * 로그인 ID와 비밀번호를 포함합니다.
 */
public record LoginRequest(
        @Email @NotBlank(message = "로그인 아이디는 필수 입력 값입니다.")
        String email,
        @NotBlank(message = "비밀번호는 필수 입력 값입니다.")
        String password
) {}
