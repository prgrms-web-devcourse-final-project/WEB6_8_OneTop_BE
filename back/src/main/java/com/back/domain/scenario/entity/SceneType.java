package com.back.domain.scenario.entity;

import com.back.global.baseentity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 시나리오의 특정 유형(경제, 행복 등)에 대한 상세 분석 정보를 저장하는 엔티티.
 */
@Entity
@Table(name = "scene_type")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SceneType extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scenarios_id")
    private Scenario scenario;

    @Enumerated(EnumType.STRING)
    private Type type;

    private int point;

    @Column(columnDefinition = "TEXT")
    private String analysis;
}