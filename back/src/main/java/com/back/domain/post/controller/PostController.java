package com.back.domain.post.controller;

import com.back.domain.post.dto.PostDetailResponse;
import com.back.domain.post.dto.PostRequest;
import com.back.domain.post.dto.PostSearchCondition;
import com.back.domain.post.dto.PostSummaryResponse;
import com.back.domain.post.service.PostService;
import com.back.global.common.PageResponse;
import com.back.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
            @AuthenticationPrincipal CustomUserDetails cs
            ) {
        PostDetailResponse response = postService.createPost(cs.getUser().getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 게시글 목록 조회
    @GetMapping
    @Operation(summary = "게시글 목록 조회", description = "게시글 목록을 조회합니다.")
    public ResponseEntity<PageResponse<PostSummaryResponse>> getPosts(
            @Parameter(description = "검색 조건") @ModelAttribute PostSearchCondition condition,
            @Parameter(description = "페이지 정보") Pageable pageable,
            @AuthenticationPrincipal CustomUserDetails cs) {
        Page<PostSummaryResponse> responses = postService.getPosts(cs.getUser().getId(), condition, pageable);
        return ResponseEntity.ok(PageResponse.of(responses));
    }

    // 게시글 단건 조회
    @GetMapping("/{postId}")
    @Operation(summary = "게시글 상세 조회", description = "게시글 ID로 게시글을 조회합니다.")
    public ResponseEntity<PostDetailResponse> getPost(
            @Parameter(description = "조회할 게시글 ID", required = true) @PathVariable Long postId,
            @AuthenticationPrincipal CustomUserDetails cs) {
        return ResponseEntity.ok(postService.getPost(cs.getUser().getId(), postId));
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
            @AuthenticationPrincipal CustomUserDetails cs) {
        return ResponseEntity.ok(postService.updatePost(cs.getUser().getId(), postId, request));
    }

    @DeleteMapping("/{postId}")
    @Operation(summary = "게시글 삭제", description = "게시글 ID로 게시글을 삭제합니다.")
    public ResponseEntity<Void> deletePost(
            @Parameter(description = "삭제할 게시글 ID", required = true) @PathVariable Long postId,
            @AuthenticationPrincipal CustomUserDetails cs) {
        postService.deletePost(cs.getUser().getId(), postId);
        return ResponseEntity.ok(null);
    }
}