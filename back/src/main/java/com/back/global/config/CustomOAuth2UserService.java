package com.back.global.config;

import com.back.domain.user.entity.AuthProvider;
import com.back.domain.user.entity.Role;
import com.back.domain.user.entity.User;
import com.back.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final UserRepository userRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate = new DefaultOAuth2UserService();
        OAuth2User oAuth2User = delegate.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        String userNameAttributeName = userRequest.getClientRegistration().getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName();

        OAuthAttributes attributes = OAuthAttributes.of(registrationId, userNameAttributeName, oAuth2User.getAttributes());

        User user = saveOrUpdate(attributes, registrationId);

        return new CustomUserDetails(user, oAuth2User.getAttributes());
    }

    private User saveOrUpdate(OAuthAttributes attributes, String registrationId) {
        Optional<User> userOptional = userRepository.findByEmail(attributes.getEmail());
        User user;

        if (userOptional.isPresent()) {
            user = userOptional.get();
            // 이미 존재하는 유저라면 정보 업데이트 (예: 닉네임, 프로필 이미지 등)
            // user.update(attributes.getName(), attributes.getPicture());
        } else {
            // 새로운 유저라면 회원가입 처리
            AuthProvider authProvider = AuthProvider.valueOf(registrationId.toUpperCase());
            user = User.builder()
                    .loginId(attributes.getEmail()) // OAuth2에서는 이메일을 loginId로 사용
                    .email(attributes.getEmail())
                    .nickname(attributes.getName())
                    .role(Role.USER)
                    .authProvider(authProvider) // AuthProvider 추가
                    .password("oauth2_user") // OAuth2 유저는 비밀번호가 필요 없음 (임시 값)
                    .birthdayAt(LocalDateTime.now()) // 임시 값
                    .gender(null) // 임시 값
                    .mbti(null) // 임시 값
                    .beliefs("OAuth2 User") // 임시 값
                    .build();
        }
        return userRepository.save(user);
    }
}
