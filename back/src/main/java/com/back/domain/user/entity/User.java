package com.back.domain.user.entity;

import com.back.global.baseentity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDateTime;

/**
 * 사용자 정보를 저장하는 엔티티.
 * 일반 로그인, OAuth2 로그인, 게스트 로그인 사용자를 포함합니다.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = true)
    private String password;

    @Column(nullable = false, length = 30)
    private String username;

    @Column(nullable = false, unique = true, length = 80)
    private String nickname;

    @Column(nullable = false)
    private LocalDateTime birthdayAt;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    @Enumerated(EnumType.STRING)
    private Mbti mbti;

    private String beliefs;

    private String lifeSatis;

    private String relationship;

    private String workLifeBal;

    private String riskAvoid;

    @Enumerated(EnumType.STRING)
    private AuthProvider authProvider;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}