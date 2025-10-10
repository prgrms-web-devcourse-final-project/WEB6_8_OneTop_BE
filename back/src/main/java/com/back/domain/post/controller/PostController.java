package com.back.domain.post.controller;

import com.back.domain.post.dto.PostDetailResponse;
import com.back.domain.post.dto.PostRequest;
import com.back.domain.post.dto.PostSearchCondition;
import com.back.domain.post.dto.PostSummaryResponse;
import com.back.domain.post.enums.PostSortType;
import com.back.domain.post.service.PostService;
import com.back.domain.user.entity.User;
import com.back.global.common.PageResponse;
import com.back.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 게시글 관련 API 요청을 처리하는 컨트롤러.
 */
@Tag(name = "Post", description = "게시글 관련 API")
@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    // 게시글 생성
    @PostMapping
    @Operation(summary = "게시글 생성", description = "새 게시글을 생성합니다.")
    public ResponseEntity<PostDetailResponse> createPost(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "생성할 게시글 정보",
                    required = true
            )
            @RequestBody @Valid PostRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userDetails.getUser();
        PostDetailResponse response = postService.createPost(user, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 게시글 목록 조회
    @GetMapping
    @Operation(summary = "게시글 목록 조회", description = "게시글 목록을 조회합니다.")
    public ResponseEntity<PageResponse<PostSummaryResponse>> getPosts(
            @Parameter(description = "검색 조건") @ModelAttribute PostSearchCondition condition,
            @Parameter(description = "페이지 정보") Pageable pageable,
            @RequestParam(defaultValue = "LATEST") PostSortType sortType,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = (userDetails != null) ? userDetails.getUser() : null;

        Sort sort = Sort.by(Sort.Direction.DESC, sortType.getProperty());
        Pageable sortedPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);

        Page<PostSummaryResponse> responses = postService.getPosts(user, condition, sortedPageable);
        return ResponseEntity.ok(PageResponse.of(responses));
    }

    // 게시글 단건 조회
    @GetMapping("/{postId}")
    @Operation(summary = "게시글 상세 조회", description = "게시글 ID로 게시글을 조회합니다.")
    public ResponseEntity<PostDetailResponse> getPost(
            @Parameter(description = "조회할 게시글 ID", required = true) @PathVariable Long postId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = (userDetails != null) ? userDetails.getUser() : null;
        return ResponseEntity.ok(postService.getPost(user, postId));
    }

    @PutMapping("/{postId}")
    @Operation(summary = "게시글 수정", description = "게시글 ID로 게시글을 수정합니다.")
    public ResponseEntity<Long> updatePost(
            @Parameter(description = "수정할 게시글 ID", required = true) @PathVariable Long postId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "수정할 게시글 정보",
                    required = true
            )
            @RequestBody @Valid PostRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userDetails.getUser();
        return ResponseEntity.ok(postService.updatePost(user, postId, request));
    }

    @DeleteMapping("/{postId}")
    @Operation(summary = "게시글 삭제", description = "게시글 ID로 게시글을 삭제합니다.")
    public ResponseEntity<Void> deletePost(
            @Parameter(description = "삭제할 게시글 ID", required = true) @PathVariable Long postId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userDetails.getUser();
        postService.deletePost(user, postId);
        return ResponseEntity.ok(null);
    }
}