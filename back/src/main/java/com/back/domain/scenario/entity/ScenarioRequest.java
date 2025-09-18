package com.back.domain.scenario.entity;

import com.back.domain.user.entity.User;
import com.back.global.baseentity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDateTime;

/**
 * 시나리오 추출 요청 정보를 저장하는 엔티티.
 * AI를 통해 시나리오를 생성하기 위한 요청의 상세 정보와 상태를 관리합니다.
 */
@Entity
@Table(name = "scenario_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScenarioRequest extends BaseEntity {

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
}