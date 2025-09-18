package com.back.global.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 사용자 로그인 요청 시 필요한 정보를 담는 DTO 클래스.
 * 로그인 ID와 비밀번호를 포함합니다.
 */
@Getter
@Setter
public class LoginRequest {
    private String loginId;
    private String password;
}
