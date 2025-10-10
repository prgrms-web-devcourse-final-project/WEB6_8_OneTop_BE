package com.back.domain.comment.entity;

import com.back.domain.post.entity.Post;
import com.back.domain.user.entity.User;
import com.back.global.baseentity.BaseEntity;
import com.back.global.exception.ApiException;
import com.back.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 댓글 엔티티.
 * 게시글에 대한 사용자 댓글 정보를 저장합니다.
 */
@Entity
@Table(name = "comments",
        indexes = {
                @Index(name = "idx_comment_post_created", columnList = "post_id, created_date desc"),
                @Index(name = "idx_comment_post_like", columnList = "post_id, like_count desc")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class Comment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    @ColumnDefault("true")
    private boolean hide;

    private int likeCount;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    public void checkUser(Long targetUserId) {
        if (!targetUserId.equals(user.getId()))
            throw new ApiException(ErrorCode.UNAUTHORIZED_USER);
    }

    public void updateContent(String content) {
        this.content = content;
    }

    public void incrementLikeCount() {
        this.likeCount++;
    }

    public void decrementLikeCount() {
        if (this.likeCount > 0) {
            this.likeCount--;
        }
    }
}