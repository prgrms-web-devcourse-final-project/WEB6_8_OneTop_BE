package com.back.domain.comment.controller;

import com.back.domain.comment.dto.CommentRequest;
import com.back.domain.comment.dto.CommentResponse;
import com.back.domain.comment.enums.CommentSortType;
import com.back.domain.comment.service.CommentService;
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

@RestController
@RequestMapping("/api/v1/posts/{postId}/comments")
@RequiredArgsConstructor
@Tag(name = "Comment", description = "댓글 관련 API")
public class CommentController {

    private final CommentService commentService;

    @PostMapping
    @Operation(summary = "댓글 생성", description = "새 댓글을 생성합니다.")
    public ResponseEntity<CommentResponse> createComment(
            @RequestBody @Valid CommentRequest request,
            @PathVariable("postId") Long postId,
            @AuthenticationPrincipal CustomUserDetails cs
    ) {
        User user = cs.getUser();
        CommentResponse response = commentService.createComment(user, postId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "댓글 목록 조회", description = "게시글 목록을 조회합니다.")
    public ResponseEntity<PageResponse<CommentResponse>> getComments(
            Pageable pageable,
            @PathVariable("postId") Long postId,
            @RequestParam(defaultValue = "LATEST") CommentSortType sortType,
            @AuthenticationPrincipal CustomUserDetails cs) {

        User user = cs != null ? cs.getUser() : null;

        Sort sort = Sort.by(Sort.Direction.DESC, sortType.getProperty());
        Pageable sortedPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);

        Page<CommentResponse> responses = commentService.getComments(user, postId, sortedPageable);
        return ResponseEntity.ok(PageResponse.of(responses));
    }

    @PutMapping("/{commentId}")
    @Operation(summary = "댓글 수정", description = "자신의 댓글을 수정합니다.")
    public ResponseEntity<Long> updateComment(
            @PathVariable Long commentId,
            @RequestBody @Valid CommentRequest request,
            @AuthenticationPrincipal CustomUserDetails cs) {

        User user = cs.getUser();
        return ResponseEntity.ok(commentService.updateComment(user, commentId, request));
    }

    @DeleteMapping("/{commentId}")
    @Operation(summary = "댓글 삭제", description = "자신의 댓글을 삭제합니다.")
    public ResponseEntity<Void> deleteComment(
            @PathVariable Long commentId,
            @AuthenticationPrincipal CustomUserDetails cs) {

        User user = cs.getUser();
        commentService.deleteComment(user, commentId);
        return ResponseEntity.ok().build();
    }
}
