package com.back.domain.session.entity;

import com.back.domain.user.entity.User;
import com.back.global.baseentity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;

/**
 * 사용자 세션 정보를 저장하는 엔티티.
 * 로그인된 사용자 또는 게스트의 세션 상태를 관리합니다.
 */
@Entity
@Table(name = "sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Session extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @ColumnDefault("'GUEST'")
    private SessionType sessionKind;

    @Column(unique = true, nullable = false, length = 191)
    private String jwtId;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    @ColumnDefault("false")
    private boolean revoked;
}