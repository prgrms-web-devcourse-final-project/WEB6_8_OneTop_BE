package com.back.domain.post.controller;

import com.back.domain.post.dto.PostRequest;
import com.back.domain.post.dto.PostResponse;
import com.back.domain.post.service.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    public ResponseEntity<PostResponse> createPost(
            @RequestBody @Valid PostRequest request) {
        Long userId = 1L; // fixme 임시 사용자 ID
        PostResponse response = postService.createPost(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 게시글 목록 조회
    @GetMapping
    public ResponseEntity<List<PostResponse>> getPosts() {
        List<PostResponse> responses = postService.getPosts();
        return ResponseEntity.ok(responses);
    }

    // 게시글 단건 조회
    @GetMapping("/{postId}")
    public ResponseEntity<PostResponse> getPost(@PathVariable Long postId) {
        return ResponseEntity.ok(postService.getPost(postId));
    }

    @PutMapping("/{postId}")
    public ResponseEntity<PostResponse> updatePost(
            @PathVariable Long postId,
            @RequestBody @Valid PostRequest request) {
        Long userId = 1L; // fixme 임시 사용자 ID
        return ResponseEntity.ok(postService.updatePost(userId, postId, request));
    }

    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> deletePost(@PathVariable Long postId) {
        Long userId = 1L; // fixme 임시 사용자 ID
        postService.deletePost(userId, postId);
        return ResponseEntity.ok().build();
    }
}