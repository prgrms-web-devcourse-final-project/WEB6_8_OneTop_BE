package com.back.domain.post.mapper;

import com.back.domain.post.dto.PostRequest;
import com.back.domain.post.dto.PostResponse;
import com.back.domain.post.entity.Post;

import java.util.List;

/**
 * PostMapper
 * 엔티티와 PostRequest, PostResponse 간의 변환을 담당하는 매퍼 클래스
 */
public abstract class PostMapper {
    public static Post toEntity(PostRequest request) {
        return Post.builder()
                .title(request.title())
                .content(request.content())
                .category(request.category())
                .hide(false)
                .likeCount(0)
                .build();
    }

    public static PostResponse toResponse(Post post) {
        return new PostResponse(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                post.getCategory(),
                post.isHide(),
                post.getLikeCount(),
                post.getCreatedDate()
        );
    }

    public static List<PostResponse> toResponseList(List<Post> posts) {
        return posts.stream()
                .map(PostMapper::toResponse)
                .toList();
    }
}