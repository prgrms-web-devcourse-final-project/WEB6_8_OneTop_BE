package com.back.domain.user.controller;

import com.back.domain.user.dto.*;
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

/**
 * 사용자 정보 관리 컨트롤러 (마이 페이지)
 * 사용자 정보 조회/수정, 통계, 작성 게시글/댓글 목록, 시나리오 목록 조회, 대표프로필 선택/조회 API 제공
 */
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
        return ResponseEntity.ok(userInfoService.saveOrUpdateMyInfo(principal.getId(), req));
    }

    // 사용자 정보 수정
    @PutMapping("/users-info")
    public ResponseEntity<UserInfoResponse> updateMyInfo(@AuthenticationPrincipal CustomUserDetails principal,
                                                         @Valid @RequestBody UserInfoRequest req) {
        return ResponseEntity.ok(userInfoService.saveOrUpdateMyInfo(principal.getId(), req));
    }

    // 내가 만든 시나리오 목록 조회 (평행우주 목록)
    @GetMapping("/users/list")
    public ResponseEntity<PageResponse<UserScenarioListResponse>> getMyScenarios(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PageableDefault(size = 5) Pageable pageable) {
        return ResponseEntity.ok(userInfoService.getMyScenarios(principal.getId(), pageable));
    }

    // 내 게시글 목록 조회
    @GetMapping("/users/my-posts")
    public ResponseEntity<PageResponse<UserPostListResponse>> getMyPosts(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PageableDefault(size = 5) Pageable pageable) {
        return ResponseEntity.ok(userInfoService.getMyPosts(principal.getId(), pageable));
    }

    // 내 댓글 목록 조회
    @GetMapping("/users/my-comments")
    public ResponseEntity<PageResponse<UserCommentListResponse>> getMyComments(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PageableDefault(size = 5) Pageable pageable) {
        return ResponseEntity.ok(userInfoService.getMyComments(principal.getId(), pageable));
    }

    // 대표 시나리오 설정
    @PutMapping("/users/profile-scenario")
    public ResponseEntity<Void> setProfileScenario(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestParam Long scenarioId) {
        userInfoService.setProfileScenario(principal.getId(), scenarioId);
        return ResponseEntity.ok().build();
    }

    // 대표 프로필 조회
    @GetMapping("/users/profile")
    public ResponseEntity<UserProfileResponse> getMyProfile(
            @AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(userInfoService.getMyProfile(principal.getId()));
    }
}
