package com.back.domain.user.controller;

import com.back.domain.user.dto.GuestLoginResponse;
import com.back.domain.user.dto.LoginRequest;
import com.back.domain.user.dto.SignupRequest;
import com.back.domain.user.dto.UserResponse;
import com.back.domain.user.entity.User;
import com.back.domain.user.service.GuestService;
import com.back.domain.user.service.UserService;
import com.back.global.common.ApiResponse;
import com.back.global.config.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

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
                .body(ApiResponse.success(UserResponse.from(saved), "성공적으로 생성되었습니다."));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<UserResponse>> login(@Valid @RequestBody LoginRequest req, HttpServletRequest request){
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.email(), req.password()));
        SecurityContextHolder.getContext().setAuthentication(auth);
        request.getSession(true);
        CustomUserDetails cud = (CustomUserDetails) auth.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(UserResponse.from(cud.getUser()), "로그인 성공"));
    }

    @PostMapping("/guest")
    public ResponseEntity<ApiResponse<GuestLoginResponse>> guestLogin(){
        User savedGuest = guestService.createAndSaveGuest();

        CustomUserDetails cud = new CustomUserDetails(savedGuest);
        Authentication auth = new UsernamePasswordAuthenticationToken(cud, null, cud.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        return ResponseEntity.ok(ApiResponse.success(GuestLoginResponse.from(savedGuest), "게스트 로그인 성공"));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> me(@AuthenticationPrincipal CustomUserDetails cud) {
        if (cud == null) {
            return ResponseEntity.ok(ApiResponse.success(null, "anonymous"));
        }
        return ResponseEntity.ok(ApiResponse.success(UserResponse.from(cud.getUser()), "authenticated"));
    }
}
