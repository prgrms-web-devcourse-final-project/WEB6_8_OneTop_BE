package com.back.global.security.oauth2;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

/**
 * OAuth2 로그인 실패 시 호출되는 핸들러.
 * 로그인 실패 정보를 클라이언트로 리다이렉트하여 전달합니다.
 */
@Slf4j
@Component
public class OAuth2FailureHandler extends SimpleUrlAuthenticationFailureHandler {

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException, ServletException {
        // OAuth2 로그인 실패 시 에러 메시지를 포함하여 클라이언트로 리다이렉트
        log.error("OAuth2 Login Failure: {}", exception.getMessage());

        String targetUrl = UriComponentsBuilder.fromUriString("http://localhost:3000/oauth2/redirect") // 클라이언트 리다이렉트 URL
                .queryParam("error", exception.getMessage())
                .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
