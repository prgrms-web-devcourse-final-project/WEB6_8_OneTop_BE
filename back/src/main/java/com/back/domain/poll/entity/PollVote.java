package com.back.domain.poll.entity;

import com.back.domain.post.entity.Post;
import com.back.domain.user.entity.User;
import com.back.global.baseentity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/**
 * 투표 참여 정보를 저장하는 엔티티.
 * 사용자가 특정 게시글의 투표에 참여한 내역을 기록합니다.
 */
@Entity
@Table(name = "poll_votes",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_logged_in_once", columnNames = {"post_id", "pollUid", "user_id"}),
        @UniqueConstraint(name = "uq_anonymous_once", columnNames = {"post_id", "pollUid", "userHash"})
    }
)
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
@AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
@Builder
public class PollVote extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @Column(nullable = false)
    private UUID pollUid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(length = 128)
    private String userHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String choiceJson;

}