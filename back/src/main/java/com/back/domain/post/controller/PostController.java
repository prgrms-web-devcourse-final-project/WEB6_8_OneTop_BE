package com.back.domain.post.controller;

import com.back.domain.post.dto.PostRequest;
import com.back.domain.post.dto.PostDetailResponse;
import com.back.domain.post.dto.PostSearchCondition;
import com.back.domain.post.dto.PostSummaryResponse;
import com.back.domain.post.service.PostService;
import com.back.global.common.ApiResponse;
import com.back.global.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
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
    public ApiResponse<PostDetailResponse> createPost(
            @RequestBody @Valid PostRequest request) {
        Long userId = 1L; // fixme 임시 사용자 ID
        PostDetailResponse response = postService.createPost(userId, request);
        return ApiResponse.success(response, "성공적으로 생성되었습니다.", HttpStatus.OK);
    }

    // 게시글 목록 조회
    @GetMapping
    @Operation(summary = "게시글 목록 조회", description = "게시글 목록을 조회합니다.")
    public ApiResponse<PageResponse<PostSummaryResponse>> getPosts(
            @Parameter(description = "검색 조건") @ModelAttribute PostSearchCondition condition,
            @Parameter(description = "페이지 정보") Pageable pageable) {
        Long userId = 1L;
        Page<PostSummaryResponse> responses = postService.getPosts(userId, condition, pageable);
        return ApiResponse.success(PageResponse.of(responses), "성공적으로 조회되었습니다.", HttpStatus.OK);
    }

    // 게시글 단건 조회
    @GetMapping("/{postId}")
    @Operation(summary = "게시글 상세 조회", description = "게시글 ID로 게시글을 조회합니다.")
    public ApiResponse<PostDetailResponse> getPost(
            @Parameter(description = "조회할 게시글 ID", required = true) @PathVariable Long postId) {
        Long userId = 1L; // fixme 임시 사용자 ID
        return ApiResponse.success(postService.getPost(userId, postId), "성공적으로 조회되었습니다.", HttpStatus.OK);
    }

    @PutMapping("/{postId}")
    @Operation(summary = "게시글 수정", description = "게시글 ID로 게시글을 수정합니다.")
    public ApiResponse<Long> updatePost(
            @Parameter(description = "수정할 게시글 ID", required = true) @PathVariable Long postId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "수정할 게시글 정보",
                    required = true
            )
            @RequestBody @Valid PostRequest request) {
        Long userId = 1L; // fixme 임시 사용자 ID
        return ApiResponse.success(postService.updatePost(userId, postId, request), "성공적으로 수정되었습니다.", HttpStatus.OK);
    }

    @DeleteMapping("/{postId}")
    @Operation(summary = "게시글 삭제", description = "게시글 ID로 게시글을 삭제합니다.")
    public ApiResponse<Void> deletePost(
            @Parameter(description = "삭제할 게시글 ID", required = true) @PathVariable Long postId) {
        Long userId = 1L; // fixme 임시 사용자 ID
        postService.deletePost(userId, postId);
        return ApiResponse.success(null, "성공적으로 삭제되었습니다.", HttpStatus.OK);
    }
}