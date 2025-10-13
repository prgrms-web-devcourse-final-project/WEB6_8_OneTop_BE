package com.back.global.security.oauth2;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

/**
 * OAuth2 로그인 과정에서 인증에 실패했을 때 호출되는 핸들러 클래스입니다.
 * 로그인 실패 정보를 클라이언트로 리다이렉트하여 전달합니다.
 */
@Slf4j
@Component
public class OAuth2FailureHandler extends SimpleUrlAuthenticationFailureHandler {

    @Value("${custom.site.frontUrl}")
    private String frontUrl;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException, ServletException {
        log.error("OAuth2 Login Failure: {}", exception.getMessage());

        String code = mapToErrorCode(exception);

        String targetUrl = UriComponentsBuilder.fromUriString(frontUrl)
                .queryParam("status", "error")
                .queryParam("code", code)
                .build(true)
                .toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

    private String mapToErrorCode(AuthenticationException ex) {
        String msg = (ex.getMessage() == null ? "" : ex.getMessage()).toLowerCase();

        if (msg.contains("access_denied")) return "ACCESS_DENIED";
        if (msg.contains("email")) return "EMAIL_MISSING";
        if (msg.contains("invalid_state") || msg.contains("state")) return "INVALID_STATE";
        if (msg.contains("temporarily_unavailable") || msg.contains("server_error")) return "PROVIDER_UNAVAILABLE";
        if (msg.contains("invalid_client") || msg.contains("unauthorized_client")) return "CLIENT_CONFIG_ERROR";
        return "OAUTH2_FAILURE";
    }

}
