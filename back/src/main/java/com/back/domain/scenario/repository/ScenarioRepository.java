package com.back.domain.scenario.repository;

import com.back.domain.scenario.entity.Scenario;
import com.back.domain.scenario.entity.ScenarioStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    // 사용자별 시나리오 조회 (권한 검증, baseLine.scenarios fetch)
    @Query("SELECT s FROM Scenario s LEFT JOIN FETCH s.baseLine bl LEFT JOIN FETCH bl.scenarios WHERE s.id = :id AND s.user.id = :userId")
    Optional<Scenario> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    // 시나리오 상태 폴링용 조회 (권한 검증 포함, 최소 데이터만 로딩)
    @Query("SELECT s FROM Scenario s WHERE s.id = :id AND s.user.id = :userId")
    Optional<Scenario> findByIdAndUserIdForStatusCheck(@Param("id") Long id, @Param("userId") Long userId);

    // 베이스 시나리오 조회
    Optional<Scenario> findByBaseLineIdAndDecisionLineIsNull(Long baseLineId);

    // 베이스 시나리오 존재 확인
    boolean existsByBaseLineIdAndDecisionLineIsNull(Long baseLineId);

    // DecisionLine 기반 시나리오 조회 (중복 생성 방지 및 재시도용)
    @Query("SELECT s FROM Scenario s WHERE s.decisionLine.id = :decisionLineId")
    Optional<Scenario> findByDecisionLineId(@Param("decisionLineId") Long decisionLineId);

    // AI 생성용 시나리오 조회 - 1단계: DecisionLine + DecisionNodes
    @Query("SELECT DISTINCT s FROM Scenario s " +
           "LEFT JOIN FETCH s.decisionLine dl " +
           "LEFT JOIN FETCH dl.decisionNodes dn " +
           "WHERE s.id = :id")
    Optional<Scenario> findByIdWithDecisionNodes(@Param("id") Long id);

    // AI 생성용 시나리오 조회 - 2단계: BaseLine + BaseNodes + User
    @Query("SELECT DISTINCT s FROM Scenario s " +
           "LEFT JOIN FETCH s.decisionLine dl " +
           "LEFT JOIN FETCH dl.baseLine bl " +
           "LEFT JOIN FETCH bl.baseNodes bn " +
           "LEFT JOIN FETCH bl.user " +
           "WHERE s.id = :id")
    Optional<Scenario> findByIdWithDecisionLineAndBaseLine(@Param("id") Long id);

    // DecisionLine 기반 시나리오 조회 (권한 검증 포함, baseLine.scenarios fetch)
    @Query("SELECT s FROM Scenario s LEFT JOIN FETCH s.baseLine bl LEFT JOIN FETCH bl.scenarios WHERE s.decisionLine.id = :decisionLineId AND s.user.id = :userId")
    Optional<Scenario> findByDecisionLineIdAndUserId(
        @Param("decisionLineId") Long decisionLineId,
        @Param("userId") Long userId
    );

    // 내 완료된 선택 시나리오 목록 조회 (베이스 시나리오 제외)
    Page<Scenario> findByUserIdAndDecisionLineIsNotNullAndStatusOrderByCreatedDateDesc(
            Long userId,
            ScenarioStatus status,
            Pageable pageable
    );

    @Query("SELECT COALESCE(SUM(s.total), 0) FROM Scenario s WHERE s.user.id = :userId")
    int sumTotalByUserId(Long userId);

    int countByUserId(Long userId);

    Optional<Scenario> findByUserIdAndRepresentativeTrue(Long userId);

    boolean existsByDecisionLine_Id(Long decisionLineId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Scenario s WHERE s.id IN :scenarioIds")
    void deleteByIdIn(List<Long> scenarioIds);

    @Query("select s.id from Scenario s where s.baseLine.id = :baseLineId")
    List<Long> findIdsByBaseLine_Id(@Param("baseLineId") Long baseLineId);

    @Modifying(clearAutomatically = true)
    @Query("""
    UPDATE Scenario s
       SET s.representative = 
           CASE WHEN s.id = :scenarioId THEN true ELSE false END
     WHERE s.user.id = :userId
       AND (s.representative = true OR s.id = :scenarioId)
    """)
    void updateRepresentativeStatus(
            @Param("userId") Long userId,
            @Param("scenarioId") Long scenarioId
    );
}