package com.back.domain.post.entity;

import com.back.domain.user.entity.User;
import com.back.global.baseentity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDateTime;

/**
 * 게시글 엔티티.
 * 사용자가 작성한 게시글의 정보를 저장합니다.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Post extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(length = 200)
    private String title;

    @Column(length = 200)
    private String category;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "jsonb")
    private String voteContent;

    @Column(nullable = false)
    @ColumnDefault("true")
    private boolean hide;

    private int likeCount;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}