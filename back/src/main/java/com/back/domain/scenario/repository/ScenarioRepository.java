package com.back.domain.scenario.repository;

import com.back.domain.scenario.entity.Scenario;
import com.back.domain.scenario.entity.ScenarioStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 시나리오 엔티티에 대한 데이터베이스 접근을 담당하는 JpaRepository.
 */
@Repository
public interface ScenarioRepository extends JpaRepository<Scenario, Long> {

    // 사용자별 시나리오 조회 (권한 검증)
    Optional<Scenario> findByIdAndUserId(Long id, Long userId);

    // 베이스 시나리오 존재 확인
    boolean existsByDecisionLine_BaseLineId(Long baseLineId);

    // 베이스 시나리오 조회
    Optional<Scenario> findByBaseLineIdAndDecisionLineIsNull(Long baseLineId);

    // 베이스 시나리오 존재 확인
    boolean existsByBaseLineIdAndDecisionLineIsNull(Long baseLineId);

    // 베이스 시나리오 조회 (비교 기준)
    Optional<Scenario> findFirstByDecisionLine_BaseLineIdOrderByCreatedDateAsc(Long baseLineId);

    // 연관 Entity 정보 포함 조회 (EntityGraph로 N+1 쿼리 방지, 확장 기능 대비)
    @EntityGraph(attributePaths = {"user", "decisionLine", "sceneCompare"})
    Optional<Scenario> findWithAllRelationsById(Long id);

    // 사용자별 특정 상태 시나리오 목록 조회
    List<Scenario> findByUserIdAndStatusOrderByCreatedDateDesc(Long userId, ScenarioStatus status);

    // DecisionLine 기반 시나리오 존재 확인 (시나리오 중복 생성 방지)
    boolean existsByDecisionLineIdAndStatus(Long decisionLineId, ScenarioStatus status);

    // 베이스라인별 완료된 시나리오 조회 (비교용)
    List<Scenario> findByDecisionLine_BaseLineIdAndStatusOrderByCreatedDateAsc(Long baseLineId, ScenarioStatus status);

    // 사용자의 최신 시나리오 조회 (확장 기능 대비)
    Optional<Scenario> findTopByUserIdOrderByCreatedDateDesc(Long userId);

    // 사용자별 베이스라인 시나리오 제외 시나리오 목록 조회 (MyPage용, 페이징구현 및 N+1 방지)
    @EntityGraph(attributePaths = {"user", "decisionLine", "decisionLine.baseLine"})
    @Query("SELECT s FROM Scenario s " +
           "WHERE s.user.id = :userId " +
           "AND s.decisionLine.baseLine.id = :baseLineId " +
           "AND s.status = :status " +
           "AND s.id != (SELECT MIN(s2.id) FROM Scenario s2 " +
                        "WHERE s2.decisionLine.baseLine.id = :baseLineId " +
                        "AND s2.status = :status) " +
           "ORDER BY s.createdDate DESC")
    Page<Scenario> findUserNonBaseScenariosByBaseLineId(@Param("userId") Long userId,
                                                       @Param("baseLineId") Long baseLineId,
                                                       @Param("status") ScenarioStatus status,
                                                       Pageable pageable);

    // 특정 상태의 시나리오들 조회 (상태별 처리용)
    List<Scenario> findByStatusOrderByCreatedDateAsc(ScenarioStatus status);
}