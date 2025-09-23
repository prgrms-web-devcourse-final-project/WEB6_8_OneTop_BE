package com.back.domain.scenario.repository;

import com.back.domain.scenario.entity.SceneType;
import com.back.domain.scenario.entity.Type;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 시나리오 유형 엔티티에 대한 데이터베이스 접근을 담당하는 JpaRepository.
 */
@Repository
public interface SceneTypeRepository extends JpaRepository<SceneType, Long> {

    // 특정 시나리오의 지표들 조회 (타입 순서대로)
    List<SceneType> findByScenarioIdOrderByTypeAsc(Long scenarioId);

    // 특정 시나리오의 특정 지표 조회
    Optional<SceneType> findByScenarioIdAndType(Long scenarioId, Type type);

    // 시나리오별 지표 존재 여부 확인
    boolean existsByScenarioId(Long scenarioId);
}