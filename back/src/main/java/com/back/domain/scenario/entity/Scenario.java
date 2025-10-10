package com.back.domain.scenario.entity;

import com.back.domain.node.entity.BaseLine;
import com.back.domain.node.entity.DecisionLine;
import com.back.domain.user.entity.User;
import com.back.global.baseentity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDateTime;

/**
 * 시나리오 엔티티.
 * AI를 통해 생성된 시나리오의 상세 정보와 처리 상태를 저장합니다.
 */
@Entity
@Table(name = "scenarios",
    indexes = {
        @Index(name = "idx_scenario_user_status", columnList = "user_id, status, created_date"),
        @Index(name = "idx_scenario_decision_line", columnList = "decision_line_id"),
        @Index(name = "idx_scenario_baseline", columnList = "base_line_id")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_scenario_decision_line", columnNames = "decision_line_id")
    }
)
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

    // 시나리오 생성의 기반이 된 선택 경로
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decision_line_id")
    private DecisionLine decisionLine;

    // 시나리오가 속한 베이스라인 (하나의 BaseLine에 여러 Scenario 가능)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "base_line_id", nullable = false)
    private BaseLine baseLine;

    // 시나리오 처리 상태 (PENDING, PROCESSING, COMPLETED, FAILED)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScenarioStatus status;

    // 시나리오 생성 실패 시 오류 메시지
    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    // 시나리오 상태 변경 시점 추적용
    @LastModifiedDate
    private LocalDateTime updatedDate;

    // AI가 생성한 직업 정보
    @Column(length = 200)
    private String job;

    // 종합 점수 (5개 지표의 합계)
    private int total;

    // AI가 생성한 시나리오 요약
    @Column(columnDefinition = "TEXT")
    private String summary;

    // AI가 생성한 상세 시나리오 내용
    @Column(columnDefinition = "TEXT")
    private String description;

    // 타임라인 제목들을 JSON 형태로 저장
    @Column(columnDefinition = "TEXT")
    private String timelineTitles;  // {"2020": "대학원 진학", "2022": "연구실 변경", "2025": "해외 학회"} 형태

    // 시나리오 대표 이미지 URL
    private String img;

    // 대표 시나리오 여부
    private boolean representative;
}