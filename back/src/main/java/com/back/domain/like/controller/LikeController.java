package com.back.domain.like.controller;

import com.back.domain.like.service.LikeService;
import com.back.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    public ResponseEntity<Void> addLike(@PathVariable Long postId, @AuthenticationPrincipal CustomUserDetails cs) {
        likeService.addLike(cs.getUser().getId(), postId);
        return ResponseEntity.status(HttpStatus.CREATED).body(null);
    }

    @DeleteMapping("/{postId}/likes")
    public ResponseEntity<Void> removeLike(@PathVariable Long postId, @AuthenticationPrincipal CustomUserDetails cs) {
        likeService.removeLike(postId, cs.getUser().getId());
        return ResponseEntity.ok(null);
    }

    @PostMapping("/{postId}/comments/{commentId}/likes")
    public ResponseEntity<Void> addCommentLike(@PathVariable Long postId,
                                               @PathVariable Long commentId,
                                               @AuthenticationPrincipal CustomUserDetails cs) {
        likeService.addCommentLike(cs.getUser().getId(), postId, commentId);
        return ResponseEntity.status(HttpStatus.CREATED).body(null);
    }

    @DeleteMapping("/{postId}/comments/{commentId}/likes")
    public ResponseEntity<Void> removeCommentLike(@PathVariable Long postId,
                                                  @PathVariable Long commentId,
                                                  @AuthenticationPrincipal CustomUserDetails cs) {
        likeService.removeCommentLike(cs.getUser().getId(), postId, commentId);
        return ResponseEntity.ok(null);
    }
}