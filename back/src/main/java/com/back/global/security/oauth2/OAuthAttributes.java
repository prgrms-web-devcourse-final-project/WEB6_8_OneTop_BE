package com.back.global.security.oauth2;

import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

import java.util.Map;

/**
 * OAuth2 공급자로부터 받은 사용자 정보를 표준화하여 담는 DTO record 클래스입니다.
 * Google, GitHub 등 OAuth2 제공자마다 다른 사용자 속성 키를 애플리케이션에서 일관되게 다룰 수 있도록 변환합니다.
 */
public record OAuthAttributes(
        Map<String, Object> attributes,
        String nameAttributeKey,
        String name,
        String email
) {

    public static OAuthAttributes of(String registrationId, String userNameAttributeName, Map<String, Object> attributes) {
        return switch (registrationId.toLowerCase()) {
            case "github" -> ofGithub(userNameAttributeName, attributes);
            case "google" -> ofGoogle(userNameAttributeName, attributes);
            default -> throw new OAuth2AuthenticationException(
                    new OAuth2Error("provider_not_supported", "지원하지 않는 제공자: " + registrationId, null)
            );
        };
    }

    private static OAuthAttributes ofGoogle(String userNameAttributeName, Map<String, Object> attributes) {
        return new OAuthAttributes(
                attributes,
                userNameAttributeName,
                (String) attributes.get("name"),
                (String) attributes.get("email")
        );
    }

    private static OAuthAttributes ofGithub(String userNameAttributeName, Map<String, Object> attributes) {
        String email = (String) attributes.get("email");
        String name = (String) attributes.get("name");
        if (name == null || name.isBlank()) {
            name = (String) attributes.get("login");
        }
        return new OAuthAttributes(
                attributes,
                userNameAttributeName,
                name,
                email
        );
    }
}