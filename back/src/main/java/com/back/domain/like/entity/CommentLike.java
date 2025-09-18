package com.back.domain.like.entity;

import com.back.domain.comment.entity.Comment;
import com.back.domain.user.entity.User;
import com.back.global.baseentity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 댓글 좋아요 엔티티.
 * 사용자가 특정 댓글에 좋아요를 표시한 정보를 저장합니다.
 */
@Entity
@Table(name = "comment_likes",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "comment_like_uk",
            columnNames = {"comment_id", "user_id"}
        )
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommentLike extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comment_id")
    private Comment comment;
}