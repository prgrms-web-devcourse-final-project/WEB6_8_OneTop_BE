package com.back.global.config;

import com.back.domain.user.entity.User;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * OAuth2 로그인 성공 시 호출되는 핸들러.
 * 로그인 성공 후 JWT 토큰을 생성하고 클라이언트로 리다이렉트하여 전달합니다.
 */
@Component
@Slf4j
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {

        log.info("OAuth2 로그인 성공");

        CustomUserDetails customUserDetails = (CustomUserDetails) authentication.getPrincipal();
        User user = customUserDetails.getUser();

        log.info("OAuth2 로그인 완료 - 사용자: {} ({})", user.getEmail(), user.getAuthProvider());

        response.sendRedirect("http://localhost:3000/oauth2/redirect?success=true");
    }
}