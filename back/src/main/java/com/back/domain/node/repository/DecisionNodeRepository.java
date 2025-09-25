/**
 * 결정 노드 엔티티에 대한 데이터베이스 접근을 담당하는 JpaRepository.
 */
package com.back.domain.node.repository;

import com.back.domain.node.entity.DecisionNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DecisionNodeRepository extends JpaRepository<DecisionNode, Long> {

    // 라인별 노드 리스트(나이 ASC, id ASC) — 라인 상세용
    List<DecisionNode> findByDecisionLine_IdOrderByAgeYearAscIdAsc(Long decisionLineId);
}
