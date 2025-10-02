package com.back.domain.user.dto;

import com.back.domain.post.entity.Post;

import java.time.LocalDateTime;

public record UserPostListResponse(
        Long postId,
        String title,
        LocalDateTime createdAt,
        long commentCount
) {
    public static UserPostListResponse of(Post post, long commentCount) {
        return new UserPostListResponse(
                post.getId(),
                post.getTitle(),
                post.getCreatedDate(),
                commentCount
        );
    }
}