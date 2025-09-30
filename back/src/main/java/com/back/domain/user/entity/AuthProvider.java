package com.back.domain.user.entity;

/**
 * 사용자 인증 제공자(Provider)를 정의하는 Enum.
 * 일반 로그인, 소셜 로그인(Google, GitHub), 게스트 로그인 등을 포함합니다.
 */
public enum AuthProvider {
    LOCAL, GOOGLE, GITHUB, GUEST
}