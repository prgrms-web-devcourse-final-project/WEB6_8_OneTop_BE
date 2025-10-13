package com.back.global.security.oauth2;

import com.back.domain.user.entity.User;
import com.back.global.security.CustomUserDetails;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * OAuth2 로그인 성공 시 호출되는 핸들러 클래스입니다.
 * 로그인 성공시 프론트엔드 클라이언트로 리다이렉트합니다.
 */
@Component
@Slf4j
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    @Value("${custom.site.frontUrl}")
    private String frontUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {

        log.info("OAuth2 로그인 성공");

        CustomUserDetails customUserDetails = (CustomUserDetails) authentication.getPrincipal();
        User user = customUserDetails.getUser();

        log.info("OAuth2 로그인 완료 - 사용자: {} ({})", user.getEmail(), user.getAuthProvider());

        response.sendRedirect(frontUrl);
    }
}