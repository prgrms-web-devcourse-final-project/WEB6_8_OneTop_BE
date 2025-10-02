package com.back.domain.user.dto;

import com.back.domain.comment.entity.Comment;

import java.time.LocalDateTime;

public record UserCommentListResponse(
        Long commentId,
        Long postId,
        String postTitle,
        String content,
        LocalDateTime postCreatedAt,
        LocalDateTime commentCreatedAt
) {
    public static UserCommentListResponse from(Comment comment) {
        return new UserCommentListResponse(
                comment.getId(),
                comment.getPost().getId(),
                comment.getPost().getTitle(),
                comment.getContent(),
                comment.getPost().getCreatedDate(),
                comment.getCreatedDate()
        );
    }
}