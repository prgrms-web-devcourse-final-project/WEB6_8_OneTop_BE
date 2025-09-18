package com.back.global.config;

import com.back.global.config.JwtTokenProvider;
import com.back.global.config.TokenInfo;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

/**
 * OAuth2 로그인 성공 시 호출되는 핸들러.
 * 로그인 성공 후 JWT 토큰을 생성하고 클라이언트로 리다이렉트하여 전달합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        // OAuth2 로그인 성공 시 JWT 토큰을 생성하고 클라이언트로 리다이렉트
        log.info("OAuth2 Login Success!");

        TokenInfo tokenInfo = jwtTokenProvider.generateToken(authentication);

        String targetUrl = UriComponentsBuilder.fromUriString("http://localhost:3000/oauth2/redirect") // 클라이언트 리다이렉트 URL
                .queryParam("token", tokenInfo.getAccessToken())
                .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
