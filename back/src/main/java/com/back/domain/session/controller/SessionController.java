package com.back.domain.session.controller;

import com.back.domain.session.service.SessionService;
import com.back.domain.user.service.UserService;
import com.back.global.config.JwtTokenProvider;
import com.back.global.config.TokenInfo;
import com.back.global.dto.LoginRequest;
import com.back.global.dto.SignupRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * 사용자 인증 및 세션 관련 API 요청을 처리하는 컨트롤러.
 * 회원가입, 로그인, 게스트 로그인, 로그아웃 기능을 제공합니다.
 */
@RestController
@RequestMapping("/users-auth")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;
    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;

    @PostMapping("/signup")
    public ResponseEntity<String> signup(@Valid @RequestBody SignupRequest signupRequest) {
        // 사용자 회원가입 처리
        userService.signup(signupRequest);
        return ResponseEntity.ok("회원가입 성공");
    }

    @PostMapping("/login")
    public ResponseEntity<TokenInfo> login(@RequestBody LoginRequest loginRequest, HttpServletRequest request, HttpServletResponse response) {
        // 세션 기반 로그인 후 JWT 토큰 발급
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getLoginId(), loginRequest.getPassword())
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        TokenInfo tokenInfo = jwtTokenProvider.generateToken(authentication);

        return ResponseEntity.ok(tokenInfo);
    }

    @PostMapping("/guest")
    public ResponseEntity<TokenInfo> guestLogin() {
        // 게스트 토큰 발급
        Authentication guestAuthentication = sessionService.authenticateGuest();
        SecurityContextHolder.getContext().setAuthentication(guestAuthentication);

        TokenInfo tokenInfo = jwtTokenProvider.generateToken(guestAuthentication);
        return ResponseEntity.ok(tokenInfo);
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(HttpServletRequest request, HttpServletResponse response) {
        // 로그아웃 처리
        return ResponseEntity.ok("로그아웃 성공");
    }
}
