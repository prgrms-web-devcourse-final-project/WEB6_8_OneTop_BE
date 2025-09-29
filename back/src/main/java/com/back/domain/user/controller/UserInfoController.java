package com.back.domain.user.controller;

import com.back.domain.user.dto.UserInfoRequest;
import com.back.domain.user.dto.UserInfoResponse;
import com.back.domain.user.service.UserInfoService;
import com.back.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users-info")
@RequiredArgsConstructor
public class UserInfoController {
    private final UserInfoService userInfoService;

    @GetMapping
    public ResponseEntity<UserInfoResponse> getMyInfo(@AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(userInfoService.getMyInfo(principal.getId()));
    }

    @PostMapping
    public ResponseEntity<UserInfoResponse> createMyInfo(@AuthenticationPrincipal CustomUserDetails principal,
                                                         @Valid @RequestBody UserInfoRequest req) {
        return ResponseEntity.ok(userInfoService.createMyInfo(principal.getId(), req));
    }

    @PutMapping
    public ResponseEntity<UserInfoResponse> updateMyInfo(@AuthenticationPrincipal CustomUserDetails principal,
                                                         @Valid @RequestBody UserInfoRequest req) {
        return ResponseEntity.ok(userInfoService.updateMyInfo(principal.getId(), req));
    }
}
