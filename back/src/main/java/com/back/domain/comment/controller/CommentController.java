package com.back.domain.comment.controller;

import com.back.domain.comment.dto.CommentRequest;
import com.back.domain.comment.dto.CommentResponse;
import com.back.domain.comment.service.CommentService;
import com.back.domain.post.dto.PostDetailResponse;
import com.back.domain.post.dto.PostRequest;
import com.back.domain.post.service.PostService;
import com.back.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * 댓글 관련 API 요청을 처리하는 컨트롤러.
 */
@Tag(name = "Comment", description = "댓글 관련 API")
@RestController
@RequestMapping("/api/v1/posts/{postId}/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @PostMapping
    @Operation(summary = "댓글 생성", description = "새 댓글을 생성합니다.")
    public ApiResponse<CommentResponse> createPost(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "생성할 댓글 정보",
                    required = true
            )
            @RequestBody @Valid CommentRequest request,
            @Parameter(description = "조회할 게시글 ID", required = true) @PathVariable("postId") Long postId,
            @RequestParam Long userId
    ) {
        CommentResponse response = commentService.createComment(userId, postId, request);
        return ApiResponse.success(response, "성공적으로 생성되었습니다.", HttpStatus.OK);
    }

}