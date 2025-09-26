package com.back.domain.comment.mapper;

import com.back.domain.comment.dto.CommentRequest;
import com.back.domain.comment.dto.CommentResponse;
import com.back.domain.comment.entity.Comment;
import com.back.domain.post.entity.Post;
import com.back.domain.user.entity.User;
import com.back.global.mapper.Mapper;
import com.back.global.mapper.MappingException;
import com.back.global.mapper.TwoWayMapper;


/**
 * CommentMappers
 * 댓글(Comment) 관련 엔티티 ↔ DTO 매핑 유틸리티 클래스.
 * 구성:
 * - COMMENT_READ : Comment → CommentResponse 변환 (읽기 전역 매퍼)
 * - CommentCtxMapper : CommentRequest ↔ Comment ↔ CommentResponse 변환 (쓰기 매퍼, 컨텍스트 보유)
 *     - 사용자(User), 게시글(Post) 컨텍스트를 보유하여 엔티티 생성 시 활용
 *     - 내가 쓴 댓글 여부, 좋아요 여부 등은 추후 구현 예정
 */
public final class CommentMappers {

    private CommentMappers() {}

    public static final Mapper<Comment, CommentResponse> COMMENT_READ = e -> {
        if (e == null) throw new MappingException("Comment is null");
        return new CommentResponse(
                e.getId(),
                e.isHide() ? "익명" : (e.getUser() != null ? e.getUser().getNickname() : null),
                e.getContent(),
                e.getLikeCount(),
                false, // todo : 내가 쓴 댓글 여부 추후 구현
                false, // todo : 좋아요 여부 추후 구현
                e.getCreatedDate()
        );
    };

    public static final class CommentCtxMapper implements TwoWayMapper<CommentRequest, Comment, CommentResponse> {
        private final User user;
        private final Post post;

        public CommentCtxMapper(User user, Post post) {
            this.user = user;
            this.post = post;
        }

        @Override
        public Comment toEntity(CommentRequest req) {
            if (req == null) throw new MappingException("CommentRequest is null");
            return Comment.builder()
                    .user(user)
                    .post(post)
                    .content(req.content())
                    .hide(req.hide() != null ? req.hide() : false)
                    .build();
        }

        @Override
        public CommentResponse toResponse(Comment entity) {
            return COMMENT_READ.map(entity);
        }
    }
}

