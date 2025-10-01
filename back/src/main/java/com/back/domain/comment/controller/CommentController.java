package com.back.domain.comment.controller;

import com.back.domain.comment.dto.CommentRequest;
import com.back.domain.comment.dto.CommentResponse;
import com.back.domain.comment.enums.CommentSortType;
import com.back.domain.comment.service.CommentService;
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

@Tag(name = "Comment", description = "댓글 관련 API")
@RestController
@RequestMapping("/api/v1/posts/{postId}/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    // 댓글 생성
    @PostMapping
    @Operation(summary = "댓글 생성", description = "새 댓글을 생성합니다.")
    public ResponseEntity<CommentResponse> createPost(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "생성할 댓글 정보",
                    required = true
            )
            @RequestBody @Valid CommentRequest request,
            @Parameter(description = "조회할 게시글 ID", required = true) @PathVariable("postId") Long postId,
            @AuthenticationPrincipal CustomUserDetails cs
    ) {
        CommentResponse response = commentService.createComment(cs.getUser().getId(), postId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "댓글 목록 조회", description = "게시글 목록을 조회합니다.")
    public ResponseEntity<PageResponse<CommentResponse>> getPosts(
            @Parameter(description = "페이지 정보") Pageable pageable,
            @Parameter(description = "조회할 게시글 ID", required = true) @PathVariable("postId") Long postId,
            @Parameter(description = "정렬 조건 LATEST or LIKES") @RequestParam(defaultValue = "LATEST") CommentSortType sortType,
            @AuthenticationPrincipal CustomUserDetails cs) {

        Sort sort = Sort.by(Sort.Direction.DESC, sortType.getProperty());

        Pageable sortedPageable = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                sort
        );

        Page<CommentResponse> responses = commentService.getComments(cs.getUser().getId(), postId, sortedPageable);
        return ResponseEntity.ok(PageResponse.of(responses));
    }


    @PutMapping("/{commentId}")
    @Operation(summary = "댓글 수정", description = "자신의 댓글을 수정합니다.")
    public ResponseEntity<Long> updateComment(
            @Parameter(description = "수정할 댓글 ID", required = true) @PathVariable Long commentId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "수정할 댓글 정보",
                    required = true
            )
            @RequestBody @Valid CommentRequest request,
            @AuthenticationPrincipal CustomUserDetails cs) {
        return ResponseEntity.ok(commentService.updateComment(cs.getUser().getId(), commentId, request));
    }

    @DeleteMapping("/{commentId}")
    @Operation(summary = "댓글 삭제", description = "자신의 댓글을 삭제합니다.")
    public ResponseEntity<Void> deletePost(
            @Parameter(description = "삭제할 댓글 ID", required = true) @PathVariable Long commentId,
            @AuthenticationPrincipal CustomUserDetails cs) {
        commentService.deleteComment(cs.getUser().getId(), commentId);
        return ResponseEntity.ok(null);
    }
}