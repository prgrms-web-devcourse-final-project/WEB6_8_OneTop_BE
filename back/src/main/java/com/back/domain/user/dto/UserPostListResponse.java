package com.back.domain.user.dto;

import com.back.domain.post.entity.Post;

import java.time.LocalDateTime;

public record UserPostListResponse(
        Long postId,
        String title,
        LocalDateTime createdAt,
        int commentCount
) {
    public static UserPostListResponse from(Post post) {
        return new UserPostListResponse(
                post.getId(),
                post.getTitle(),
                post.getCreatedDate(),
                post.getComments().size()
        );
    }
}