package com.back.domain.scenario.repository;

import com.back.domain.scenario.entity.Scenario;
import com.back.domain.scenario.entity.ScenarioStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 시나리오 엔티티에 대한 데이터베이스 접근을 담당하는 JpaRepository.
 */
@Repository
public interface ScenarioRepository extends JpaRepository<Scenario, Long> {

    // 사용자별 시나리오 조회 (권한 검증)
    Optional<Scenario> findByIdAndUserId(Long id, Long userId);

    // 베이스 시나리오 조회
    Optional<Scenario> findByBaseLineIdAndDecisionLineIsNull(Long baseLineId);

    // 베이스 시나리오 존재 확인
    boolean existsByBaseLineIdAndDecisionLineIsNull(Long baseLineId);

    // DecisionLine 기반 시나리오 조회 (중복 생성 방지 및 재시도용)
    @Query("SELECT s FROM Scenario s WHERE s.decisionLine.id = :decisionLineId")
    Optional<Scenario> findByDecisionLineId(@Param("decisionLineId") Long decisionLineId);

    // 내 완료된 선택 시나리오 목록 조회 (베이스 시나리오 제외)
    Page<Scenario> findByUserIdAndDecisionLineIsNotNullAndStatusOrderByCreatedDateDesc(
            Long userId,
            ScenarioStatus status,
            Pageable pageable
    );

    @Query("SELECT COALESCE(SUM(s.total), 0) FROM Scenario s WHERE s.user.id = :userId")
    int sumTotalByUserId(Long userId);

    int countByUserId(Long userId);
}