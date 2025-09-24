package com.back.domain.post.mapper;

import com.back.domain.post.dto.PostDetailResponse;
import com.back.domain.post.dto.PostRequest;
import com.back.domain.post.dto.PostSummaryResponse;
import com.back.domain.post.entity.Post;
import com.back.domain.user.entity.User;
import com.back.global.mapper.Mapper;
import com.back.global.mapper.MappingException;
import com.back.global.mapper.TwoWayMapper;

/**
 * PostMappers
 * - PostCtxMapper(쓰기, 컨텍스트 보유): PostRequest → Post, Post → PostDetailResponse
 * - POST_DETAIL_READ(읽기, 전역 함수형 매퍼): Post → PostDetailResponse
 * - POST_SUMMARY_READ(읽기, 전역 함수형 매퍼): Post → PostSummaryResponse
 */
public final class PostMappers {

    private PostMappers() {}

    // Post → PostDetailResponse
    public static final Mapper<Post, PostDetailResponse> POST_DETAIL_READ = e -> {
        if (e == null) throw new MappingException("Post is null");
        return new PostDetailResponse(
                e.getId(),
                e.getTitle(),
                e.getContent(),
                e.isHide() ? "익명" : (e.getUser() != null ? e.getUser().getNickname() : null),
                e.getCategory(),
                e.getLikeCount(),
                false, // TODO: 로그인 유저 좋아요 여부
                e.getCreatedDate()
        );
    };

    // Post → PostSummaryResponse
    public static final Mapper<Post, PostSummaryResponse> POST_SUMMARY_READ = e -> {
        if (e == null) throw new MappingException("Post is null");
        return new PostSummaryResponse(
                e.getId(),
                e.getTitle(),
                e.getCategory() != null ? e.getCategory().name() : null,
                e.isHide() ? "익명" : (e.getUser() != null ? e.getUser().getNickname() : null),
                e.getCreatedDate(),
                e.getComments() != null ? e.getComments().size() : 0,
                e.getLikeCount(),
                false // TODO: 로그인 유저 좋아요 여부
        );
    };

    public static final class PostCtxMapper implements TwoWayMapper<PostRequest, Post, PostDetailResponse> {
        private final User user;

        public PostCtxMapper(User user) {
            this.user = user;
        }

        // PostRequest → Post
        @Override
        public Post toEntity(PostRequest req) {
            if (req == null) throw new MappingException("PostRequest is null");
            return Post.builder()
                    .user(user)
                    .title(req.title())
                    .content(req.content())
                    .category(req.category())
                    .hide(req.hide() != null ? req.hide() : false)
                    .likeCount(0)
                    .build();
        }

        @Override
        public PostDetailResponse toResponse(Post entity) {
            return POST_DETAIL_READ.map(entity);
        }
    }
}