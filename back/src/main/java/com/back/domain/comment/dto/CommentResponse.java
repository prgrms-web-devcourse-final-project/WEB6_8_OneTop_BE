package com.back.domain.comment.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "댓글 DTO")
public record CommentResponse(
        @Schema(description = "댓글 ID", example = "1")
        Long commentId,

        @Schema(description = "댓글 작성자 닉네임 또는 익명", example = "홍길동")
        String author,

        @Schema(description = "댓글 내용", example = "좋은 글이네요!")
        String content,

        @Schema(description = "댓글 좋아요 수", example = "10")
        int likeCount,

        @Schema(description = "사용자가 해당 댓글의 작성자인지 여부", example = "true")
        boolean isMine,

        @Schema(description = "사용자가 해당 댓글에 좋아요를 눌렀는지 여부", example = "true")
        boolean isLiked,

        @Schema(description = "댓글 작성일자", example = "2025.09.23")
        LocalDateTime createdAt
) {}
