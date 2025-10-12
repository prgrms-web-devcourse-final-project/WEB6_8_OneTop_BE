package com.back.global.security.oauth2;

import com.back.domain.user.entity.AuthProvider;
import com.back.domain.user.entity.User;
import com.back.domain.user.service.UserService;
import com.back.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

/**
 * Spring Security OAuth2 로그인 시 사용자 정보를 가져오고,
 * 내부 User 엔티티와 매핑하여 처리하는 커스텀 서비스 클래스입니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
    private final UserService userService;
    private final GithubEmailFetcher githubEmailFetcher;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest req) throws OAuth2AuthenticationException {
        try {
            OAuth2User raw = delegate.loadUser(req);

            String registrationId = req.getClientRegistration().getRegistrationId(); // google/github
            String userNameAttr = req.getClientRegistration()
                    .getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName();

            OAuthAttributes attrs = OAuthAttributes.of(registrationId, userNameAttr, raw.getAttributes());
            String email = attrs.email();

            // GitHub일 때 이메일이 비어 있으면 API로 재요청
            if ("github".equalsIgnoreCase(registrationId) && (email == null || email.isBlank())) {
                String accessToken = req.getAccessToken().getTokenValue();
                email = githubEmailFetcher.fetchPrimaryEmail(accessToken);

                log.info("GitHub 추가 조회로 얻은 이메일: {}", email);
            }

            // 여전히 이메일이 null이라면 예외 처리
            if (email == null || email.isBlank()) {
                throw new OAuth2AuthenticationException(
                        new OAuth2Error("email_not_found", "OAuth2 제공자로부터 이메일을 받을 수 없습니다.", null)
                );
            }

            AuthProvider provider = AuthProvider.valueOf(registrationId.toUpperCase());
            User user = userService.upsertOAuthUser(email, attrs.name(), provider);

            return new CustomUserDetails(user, raw.getAttributes());

        } catch (OAuth2AuthenticationException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("OAuth2 사용자 로드 중 오류", ex);
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("server_error", "OAuth2 사용자 정보를 로드할 수 없습니다.", null),
                    ex
            );
        }
    }
}
