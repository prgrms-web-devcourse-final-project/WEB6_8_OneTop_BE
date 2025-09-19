package com.back.domain.scenario.entity;

import com.back.domain.post.entity.Post;
import com.back.domain.user.entity.User;
import com.back.global.baseentity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.LastModifiedDate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 시나리오 엔티티.
 * AI를 통해 생성된 시나리오의 상세 정보와 처리 상태를 저장합니다.
 */
@Entity
@Table(name = "scenarios")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Scenario extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private Long optionId;

    @Column(columnDefinition = "jsonb")
    private String constraintsJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScenarioStatus status;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id")
    private Post post;

    @Column(length = 200)
    private String job;

    private BigDecimal total;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scene_compare_id")
    private SceneCompare sceneCompare;

    private String img;
}