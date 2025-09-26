package com.back.domain.post.mapper;

import com.back.domain.post.dto.PostDetailResponse;
import com.back.domain.post.dto.PostRequest;
import com.back.domain.post.dto.PostSummaryResponse;
import com.back.domain.post.entity.Post;
import com.back.domain.user.entity.User;

import java.util.List;

/**
 * PostMapper
 * 엔티티와 PostRequest, PostResponse 간의 변환을 담당하는 매퍼 클래스
 */
public abstract class PostMappers {
    public static Post toEntity(PostRequest request, User user) {
        return Post.builder()
                .title(request.title())
                .content(request.content())
                .category(request.category())
                .user(user)
                .hide(false)
                .likeCount(0)
                .build();
    }

    public static PostDetailResponse toDetailResponse(Post post, Boolean isLiked) {
        return new PostDetailResponse(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                post.isHide() ? "익명" : post.getUser().getNickname(),
                post.getCategory(),
                post.getLikeCount(),
                isLiked,
                post.getCreatedDate()
        );
    }

    public static PostSummaryResponse toSummaryResponse(Post post, Boolean isLiked) {
        return new PostSummaryResponse(
                post.getId(),
                post.getTitle(),
                post.getCategory().name(),
                post.isHide() ? "익명" : post.getUser().getNickname(),
                post.getCreatedDate(),
                post.getComments().size(),
                post.getLikeCount(),
                isLiked
        );
    }

}