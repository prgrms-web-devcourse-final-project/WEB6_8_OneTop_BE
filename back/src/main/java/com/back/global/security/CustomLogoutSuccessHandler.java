package com.back.global.security;

import com.back.global.common.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class CustomLogoutSuccessHandler implements LogoutSuccessHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response,
                                Authentication authentication) throws IOException {

        if (authentication != null) {
            log.info("User logged out: {}", authentication.getName());
        }

        response.setStatus(HttpStatus.OK.value());
        response.setContentType("application/json;charset=UTF-8");

        ApiResponse<Map<String, String>> apiResponse = ApiResponse.success(
                Map.of("message", "로그아웃되었습니다"),
                "로그아웃이 완료되었습니다"
        );

        response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
    }
}
