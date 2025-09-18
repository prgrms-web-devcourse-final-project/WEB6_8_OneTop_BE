package com.back.global.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * JWT 토큰 정보를 담는 DTO(Data Transfer Object) 클래스.
 * Access Token과 Refresh Token, 그리고 토큰의 타입을 포함합니다.
 */
@Data
@Builder
@AllArgsConstructor
public class TokenInfo {
    private String grantType;
    private String accessToken;
    private String refreshToken;
}
