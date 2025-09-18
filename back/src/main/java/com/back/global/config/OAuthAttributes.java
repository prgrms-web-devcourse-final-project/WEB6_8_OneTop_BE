package com.back.global.config;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

/**
 * OAuth2 공급자로부터 받은 사용자 정보를 담는 DTO 클래스.
 * 각 공급자별로 다른 속성 이름을 통일하여 처리할 수 있도록 돕습니다.
 */
@Getter
@Builder
public class OAuthAttributes {
    private Map<String, Object> attributes;
    private String nameAttributeKey;
    private String name;
    private String email;
    private String picture;

    public static OAuthAttributes of(String registrationId, String userNameAttributeName, Map<String, Object> attributes) {
        // OAuth2 공급자(registrationId)에 따라 적절한 OAuthAttributes 객체를 생성
        if ("github".equals(registrationId)) {
            return ofGithub(userNameAttributeName, attributes);
        } else if ("google".equals(registrationId)) {
            return ofGoogle(userNameAttributeName, attributes);
        }
        return null; // 또는 예외 처리
    }

    private static OAuthAttributes ofGoogle(String userNameAttributeName, Map<String, Object> attributes) {
        // Google OAuth2 사용자 속성을 OAuthAttributes 객체로 변환
        return OAuthAttributes.builder()
                .name((String) attributes.get("name"))
                .email((String) attributes.get("email"))
                .picture((String) attributes.get("picture"))
                .attributes(attributes)
                .nameAttributeKey(userNameAttributeName)
                .build();
    }

    private static OAuthAttributes ofGithub(String userNameAttributeName, Map<String, Object> attributes) {
        // GitHub OAuth2 사용자 속성을 OAuthAttributes 객체로 변환
        return OAuthAttributes.builder()
                .name((String) attributes.get("login")) // GitHub는 'login' 필드를 이름으로 사용
                .email((String) attributes.get("email")) // GitHub는 이메일이 없을 수 있음
                .picture((String) attributes.get("avatar_url"))
                .attributes(attributes)
                .nameAttributeKey(userNameAttributeName)
                .build();
    }
}
