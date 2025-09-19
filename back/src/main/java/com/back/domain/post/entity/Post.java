package com.back.domain.post.entity;

import com.back.domain.post.enums.PostCategory;
import com.back.domain.user.entity.User;
import com.back.global.baseentity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDateTime;

/**
 * 게시글 엔티티.
 * 사용자가 작성한 게시글의 정보를 저장합니다.
 */
@Entity
@Getter
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

    /**
     * JSON 데이터를 단순 문자열로 저장 (예: {"option1": 10, "option2": 5})
     */
    private String voteContent;

    @Column(nullable = false)
    @ColumnDefault("true")
    private boolean hide;

    private int likeCount;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    public void assignUser(User user) {
        this.user = user;
    }
}