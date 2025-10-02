package com.back.domain.user.controller;

import com.back.domain.user.dto.UserInfoRequest;
import com.back.domain.user.dto.UserInfoResponse;
import com.back.domain.user.dto.UserScenarioListResponse;
import com.back.domain.user.dto.UserStatsResponse;
import com.back.domain.user.service.UserInfoService;
import com.back.global.common.PageResponse;
import com.back.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class UserInfoController {
    private final UserInfoService userInfoService;

    // 사용자 통계 정보 조회
    @GetMapping("/users/use-log")
    public ResponseEntity<UserStatsResponse> getMyStats(@AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(userInfoService.getMyStats(principal.getId()));
    }

    // 사용자 정보 조회
    @GetMapping("/users-info")
    public ResponseEntity<UserInfoResponse> getMyInfo(@AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(userInfoService.getMyInfo(principal.getId()));
    }

    // 사용자 정보 생성
    @PostMapping("/users-info")
    public ResponseEntity<UserInfoResponse> createMyInfo(@AuthenticationPrincipal CustomUserDetails principal,
                                                         @Valid @RequestBody UserInfoRequest req) {
        return ResponseEntity.ok(userInfoService.createMyInfo(principal.getId(), req));
    }

    // 사용자 정보 수정
    @PutMapping("/users-info")
    public ResponseEntity<UserInfoResponse> updateMyInfo(@AuthenticationPrincipal CustomUserDetails principal,
                                                         @Valid @RequestBody UserInfoRequest req) {
        return ResponseEntity.ok(userInfoService.updateMyInfo(principal.getId(), req));
    }

    // 내가 만든 시나리오 목록 조회 (평행우주 목록)
    @GetMapping("/users/list")
    public ResponseEntity<PageResponse<UserScenarioListResponse>> getMyScenarios(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PageableDefault(size = 5) Pageable pageable) {
        return ResponseEntity.ok(userInfoService.getMyScenarios(principal.getId(), pageable));
    }
}
