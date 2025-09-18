package com.back.domain.scenario.entity;

import com.back.global.baseentity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 시나리오 비교 결과를 저장하는 엔티티.
 * 두 시나리오 간의 비교 분석 정보를 포함합니다.
 */
@Entity
@Table(name = "scene_compare")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SceneCompare extends BaseEntity {

    @Column(columnDefinition = "TEXT")
    private String compareResult;

    @Enumerated(EnumType.STRING)
    private SceneCompareResultType resultType;

}
