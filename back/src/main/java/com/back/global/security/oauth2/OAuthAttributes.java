package com.back.global.security.oauth2;

import lombok.Builder;
import lombok.Getter;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

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
        return switch (registrationId.toLowerCase()) {
            case "github" -> ofGithub(userNameAttributeName, attributes);
            case "google" -> ofGoogle(userNameAttributeName, attributes);
            default -> throw new OAuth2AuthenticationException(
                    new OAuth2Error("provider_not_supported", "지원하지 않는 제공자: " + registrationId, null)
            );
        };
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
        // GitHub의 경우 이메일이 null일 수 있으므로 처리
        String email = (String) attributes.get("email");
        String name = (String) attributes.get("name");

        // name이 null이면 login을 사용
        if (name == null || name.trim().isEmpty()) {
            name = (String) attributes.get("login");
        }
        return OAuthAttributes.builder()
                .name(name)
                .email(email) // null일 수 있음
                .picture((String) attributes.get("avatar_url"))
                .attributes(attributes)
                .nameAttributeKey(userNameAttributeName)
                .build();
    }
}
