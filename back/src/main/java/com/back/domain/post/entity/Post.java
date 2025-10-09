package com.back.domain.post.entity;

import com.back.domain.comment.entity.Comment;
import com.back.domain.poll.entity.PollVote;
import com.back.domain.post.enums.PostCategory;
import com.back.domain.scenario.entity.Scenario;
import com.back.domain.user.entity.User;
import com.back.global.baseentity.BaseEntity;
import com.back.global.exception.ApiException;
import com.back.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.service.spi.ServiceException;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Entity
@Getter
@Table(name = "post",
        indexes = {
                @Index(name = "idx_post_category_created",
                        columnList = "category, created_date DESC"),
                @Index(name = "idx_post_user_created",
                        columnList = "user_id, created_date DESC"),
                @Index(name = "idx_post_title", columnList = "title")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class Post extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(length = 200)
    private String title;

    @Column(length = 20)
    @Enumerated(EnumType.STRING)
    private PostCategory category;

    @Column(columnDefinition = "TEXT")
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String voteContent; // {"options":[{"index":1,"text":"첫 번째 옵션"},{...}],"pollUid":"xxx"}

    @Column(nullable = false)
    @ColumnDefault("true")
    private boolean hide;

    private int likeCount;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Comment> comments = new ArrayList<>();

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PollVote> pollVotes = new ArrayList<>();

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scenario_id")
    private Scenario scenario;

    public void updatePost(String title, String content, PostCategory category) {
        this.title = title;
        this.content = content;
        this.category = category;
    }

    public void checkUser(Long targetUserId) {
        if (!user.getId().equals(targetUserId))
            throw new ApiException(ErrorCode.UNAUTHORIZED_USER);
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