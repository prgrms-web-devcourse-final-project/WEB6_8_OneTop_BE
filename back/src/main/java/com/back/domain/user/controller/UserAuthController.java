package com.back.domain.user.controller;

import com.back.domain.user.dto.GuestLoginResponse;
import com.back.domain.user.dto.LoginRequest;
import com.back.domain.user.dto.SignupRequest;
import com.back.domain.user.dto.UserResponse;
import com.back.domain.user.entity.User;
import com.back.domain.user.repository.UserRepository;
import com.back.domain.user.service.UserService;
import com.back.global.common.ApiResponse;
import com.back.global.config.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/users-auth")
@RequiredArgsConstructor
public class UserAuthController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/signup")
    public ApiResponse<UserResponse> signup(@Valid @RequestBody SignupRequest req){
        User saved = userService.signup(req);
        return ApiResponse.success(toDto(saved));
    }

    @PostMapping("/login")
    public ApiResponse<UserResponse> login(@Valid @RequestBody LoginRequest req){
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.getLoginIdOrEmail(), req.getPassword()));
        SecurityContextHolder.getContext().setAuthentication(auth); // 세션에 SecurityContext 저장
        CustomUserDetails cud = (CustomUserDetails) auth.getPrincipal();
        return ApiResponse.success(toDto(cud.getUser()));
    }

    @PostMapping("/logout")
    public ApiResponse<Map<String, String>> logout(HttpServletRequest request){
        HttpSession session = request.getSession(false);
        if(session != null) session.invalidate();
        SecurityContextHolder.clearContext();
        return ApiResponse.success(Map.of("message", "logged out"));
    }

    @PostMapping("/guest")
    public ApiResponse<GuestLoginResponse> guestLogin(){
        User savedGuest = guestService.createAndSaveGuest();


        CustomUserDetails cud = new CustomUserDetails(savedGuest);
        Authentication auth = new UsernamePasswordAuthenticationToken(cud, null, cud.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth); // 세션 저장


        return ApiResponse.ok(GuestLoginResponse.builder()
                .guestLoginId(savedGuest.getLoginId())
                .nickname(savedGuest.getNickname())
                .role(savedGuest.getRole().name())
                .build());
    }
}
