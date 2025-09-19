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

    // 시나리오 소유자
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 사용자가 선택한 옵션 ID (인생 선택지)
    @Column(nullable = false)
    private Long optionId;

    // AI 생성 시 적용된 제약 조건들 (JSON 형태로 저장)
    @Column(columnDefinition = "jsonb")
    private String constraintsJson;

    // 시나리오 처리 상태 (PENDING, PROCESSING, COMPLETED, FAILED)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScenarioStatus status;

    // 시나리오 생성 실패 시 오류 메시지
    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    // 시나리오 상태 변경 시점 추적용
    @LastModifiedDate
    private LocalDateTime updatedAt;

    // 시나리오와 연결된 게시글 (시나리오 공유 시 생성)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id")
    private Post post;

    // AI가 생성한 직업 정보
    @Column(length = 200)
    private String job;

    // 종합 점수 (5개 지표의 합계)
    private BigDecimal total;

    // AI가 생성한 시나리오 요약
    @Column(columnDefinition = "TEXT")
    private String summary;

    // AI가 생성한 상세 시나리오 내용
    @Column(columnDefinition = "TEXT")
    private String description;

    // 시나리오 비교 결과 (다른 시나리오와의 비교 분석)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scene_compare_id")
    private SceneCompare sceneCompare;

    // 시나리오 대표 이미지 URL
    private String img;
}