package com.back.domain.user.controller;

import com.back.domain.user.dto.GuestLoginResponse;
import com.back.domain.user.dto.LoginRequest;
import com.back.domain.user.dto.SignupRequest;
import com.back.domain.user.dto.UserResponse;
import com.back.domain.user.entity.User;
import com.back.domain.user.repository.UserRepository;
import com.back.domain.user.service.GuestService;
import com.back.domain.user.service.UserService;
import com.back.global.common.ApiResponse;
import com.back.global.config.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    private final AuthenticationManager authenticationManager;
    private final GuestService guestService;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<UserResponse>> signup(@Valid @RequestBody SignupRequest req){
        User saved = userService.signup(req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(UserResponse.from(saved), "성공적으로 생성되었습니다.", HttpStatus.CREATED));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<UserResponse>> login(@Valid @RequestBody LoginRequest req){
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.getLoginIdOrEmail(), req.getPassword()));
        SecurityContextHolder.getContext().setAuthentication(auth); // 세션에 SecurityContext 저장
        CustomUserDetails cud = (CustomUserDetails) auth.getPrincipal();
        return ResponseEntity.ok(
                ApiResponse.success(UserResponse.from(cud.getUser()), "로그인 성공", HttpStatus.OK)
        );
    }

    @PostMapping("/guest")
    public ResponseEntity<ApiResponse<GuestLoginResponse>> guestLogin(){
        User savedGuest = guestService.createAndSaveGuest();


        CustomUserDetails cud = new CustomUserDetails(savedGuest);
        Authentication auth = new UsernamePasswordAuthenticationToken(cud, null, cud.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        return ResponseEntity.ok(
                ApiResponse.success(GuestLoginResponse.from(savedGuest), "게스트 로그인 성공", HttpStatus.OK)
        );
    }
}
