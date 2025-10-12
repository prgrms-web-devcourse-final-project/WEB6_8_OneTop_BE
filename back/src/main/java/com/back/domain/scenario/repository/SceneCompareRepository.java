package com.back.domain.scenario.repository;

import com.back.domain.scenario.entity.SceneCompare;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * 시나리오 비교 결과 엔티티에 대한 데이터베이스 접근을 담당하는 JpaRepository.
 */
@Repository
public interface SceneCompareRepository extends JpaRepository<SceneCompare, Long> {

    // 특정 시나리오의 모든 비교 결과 조회 (6개: TOTAL + 5개 지표)
    List<SceneCompare> findByScenarioIdOrderByResultType(Long scenarioId);
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    void deleteByScenario_IdIn(Collection<Long> scenarioIds);
}
