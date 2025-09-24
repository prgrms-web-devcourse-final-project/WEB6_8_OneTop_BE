package com.back.domain.post.controller;

import com.back.domain.post.dto.PostRequest;
import com.back.domain.post.dto.PostDetailResponse;
import com.back.domain.post.dto.PostSearchCondition;
import com.back.domain.post.dto.PostSummaryResponse;
import com.back.domain.post.service.PostService;
import com.back.global.common.ApiResponse;
import com.back.global.common.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * 게시글 관련 API 요청을 처리하는 컨트롤러.
 */
@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    // 게시글 생성
    @PostMapping
    public ApiResponse<PostDetailResponse> createPost(
            @RequestBody @Valid PostRequest request) {
        Long userId = 1L; // fixme 임시 사용자 ID
        PostDetailResponse response = postService.createPost(userId, request);
        return ApiResponse.success(response, "성공적으로 생성되었습니다.", HttpStatus.OK);
    }

    // 게시글 목록 조회
    @GetMapping
    public ApiResponse<PageResponse<PostSummaryResponse>> getPosts(
            @ModelAttribute PostSearchCondition condition, Pageable pageable) {
        Page<PostSummaryResponse> responses = postService.getPosts(condition, pageable);
        return ApiResponse.success(PageResponse.of(responses), "성공적으로 조회되었습니다.", HttpStatus.OK);
    }

    // 게시글 단건 조회
    @GetMapping("/{postId}")
    public ApiResponse<PostDetailResponse> getPost(@PathVariable Long postId) {
        return ApiResponse.success(postService.getPost(postId), "성공적으로 조회되었습니다.", HttpStatus.OK);
    }

    @PutMapping("/{postId}")
    public ApiResponse<PostDetailResponse> updatePost(
            @PathVariable Long postId,
            @RequestBody @Valid PostRequest request) {
        Long userId = 1L; // fixme 임시 사용자 ID
        return ApiResponse.success(postService.updatePost(userId, postId, request), "성공적으로 수정되었습니다.", HttpStatus.OK);
    }

    @DeleteMapping("/{postId}")
    public ApiResponse<Void> deletePost(@PathVariable Long postId) {
        Long userId = 1L; // fixme 임시 사용자 ID
        postService.deletePost(userId, postId);
        return ApiResponse.success(null, "성공적으로 삭제되었습니다.", HttpStatus.OK);
    }
}