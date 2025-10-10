package com.back.domain.scenario.repository;

import com.back.domain.scenario.entity.SceneType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 시나리오 유형 엔티티에 대한 데이터베이스 접근을 담당하는 JpaRepository.
 */
@Repository
public interface SceneTypeRepository extends JpaRepository<SceneType, Long> {

    // 특정 시나리오의 지표들 조회 (타입 순서대로)
    List<SceneType> findByScenarioIdOrderByTypeAsc(Long scenarioId);

    @Query("SELECT st FROM SceneType st WHERE st.scenario.id IN :scenarioIds")
    List<SceneType> findByScenarioIdIn(@Param("scenarioIds") List<Long> scenarioIds);

    // 여러 시나리오의 지표들을 배치 조회 (시나리오 ID, 타입 순서대로 정렬)
    @Query("SELECT st FROM SceneType st WHERE st.scenario.id IN :scenarioIds ORDER BY st.scenario.id ASC, st.type ASC")
    List<SceneType> findByScenarioIdInOrderByScenarioIdAscTypeAsc(@Param("scenarioIds") List<Long> scenarioIds);

    List<SceneType> findByScenarioId(Long scenarioId);
}