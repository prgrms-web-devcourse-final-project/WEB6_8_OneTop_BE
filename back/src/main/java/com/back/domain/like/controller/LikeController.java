package com.back.domain.like.controller;

import com.back.domain.like.service.LikeService;
import com.back.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 좋아요 관련 API 요청을 처리하는 컨트롤러.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/posts")
public class LikeController {
    private final LikeService likeService;

    @PostMapping("/{postId}/likes")
    public ApiResponse<Void> addLike(@PathVariable Long postId, @RequestParam Long userId) {
        likeService.addLike(userId, postId);
        return ApiResponse.success(null, "좋아요 추가했습니다.", HttpStatus.OK);
    }

    @DeleteMapping("/{postId}/likes")
    public ApiResponse<Void> removeLike(@PathVariable Long postId, @RequestParam Long userId) {
        likeService.removeLike(postId, userId);
        return ApiResponse.success(null, "좋아요 취소하였습니다.", HttpStatus.OK);
    }
}