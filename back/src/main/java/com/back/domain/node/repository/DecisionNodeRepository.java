/**
 * 결정 노드 엔티티에 대한 데이터베이스 접근을 담당하는 JpaRepository.
 */
package com.back.domain.node.repository;

import com.back.domain.node.entity.DecisionNode;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DecisionNodeRepository extends JpaRepository<DecisionNode, Long> {

    // 라인별 노드 리스트(나이 ASC, id ASC) — 라인 상세용
    List<DecisionNode> findByDecisionLine_IdOrderByAgeYearAscIdAsc(Long decisionLineId);

    Optional<DecisionNode> findFirstByDecisionLine_IdOrderByAgeYearAscIdAsc(Long decisionLineId);

    @EntityGraph(attributePaths = {"decisionLine", "decisionLine.user"})
    Optional<DecisionNode> findWithLineAndUserById(Long id);

    // 같은 베이스라인에 속한 "헤드(부모 없음)" 결정 노드들만 조회
    List<DecisionNode> findByDecisionLine_BaseLine_IdAndParentIsNull(Long baseLineId);

    void deleteByDecisionLine_BaseLine_Id(Long baseLineId);
}
