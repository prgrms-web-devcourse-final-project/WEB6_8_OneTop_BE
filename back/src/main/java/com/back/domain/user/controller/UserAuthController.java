package com.back.domain.user.controller;

import com.back.domain.user.dto.LoginRequest;
import com.back.domain.user.dto.SignupRequest;
import com.back.domain.user.dto.UserResponse;
import com.back.domain.user.entity.User;
import com.back.domain.user.service.GuestService;
import com.back.domain.user.service.UserService;
import com.back.global.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

/**
 * 사용자 인증/인가 관련 기능을 제공하는 REST 컨트롤러입니다.
 * - 회원가입 (/signup): 새로운 사용자를 등록합니다.
 * - 로그인 (/login): 이메일과 비밀번호를 이용해 인증 후 세션을 생성합니다.
 * - 게스트 로그인 (/guest): 비회원 사용자용 임시 계정을 생성하고 로그인 처리합니다.
 * - 현재 사용자 조회 (/me): 현재 인증된 사용자의 정보를 반환합니다.
 */
@RestController
@RequestMapping("/api/v1/users-auth")
@RequiredArgsConstructor
public class UserAuthController {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final GuestService guestService;

    @PostMapping("/signup")
    public ResponseEntity<UserResponse> signup(@Valid @RequestBody SignupRequest req){
        User saved = userService.signup(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(saved));
    }

    @PostMapping("/login")
    public ResponseEntity<UserResponse> login(
            @Valid @RequestBody LoginRequest req,
            HttpServletRequest request,
            HttpServletResponse response)
    {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.email(), req.password()));
        SecurityContextHolder.getContext().setAuthentication(auth);

        request.getSession(true);
        new HttpSessionSecurityContextRepository()
                .saveContext(SecurityContextHolder.getContext(), request, response);

        CustomUserDetails cud = (CustomUserDetails) auth.getPrincipal();
        return ResponseEntity.ok(UserResponse.from(cud.getUser()));
    }

    @PostMapping("/guest")
    public ResponseEntity<UserResponse> guestLogin(HttpServletRequest request, HttpServletResponse response){
        User savedGuest = guestService.createAndSaveGuest();

        CustomUserDetails cud = new CustomUserDetails(savedGuest);
        Authentication auth = new UsernamePasswordAuthenticationToken(cud, null, cud.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        HttpSession session = request.getSession(true);

        session.setMaxInactiveInterval(600);
        session.setAttribute("guestId", savedGuest.getId());

        new HttpSessionSecurityContextRepository()
                .saveContext(SecurityContextHolder.getContext(), request, response);

        return ResponseEntity.ok(UserResponse.from(savedGuest));
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal CustomUserDetails cud) {
        if (cud == null) {
            return ResponseEntity.ok(null); // 익명 사용자
        }
        return ResponseEntity.ok(UserResponse.from(cud.getUser()));
    }
}
