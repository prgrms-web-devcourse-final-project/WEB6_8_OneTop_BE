package com.back.domain.node.repository;

import com.back.domain.node.entity.BaseNode;
import com.back.domain.user.entity.User;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 베이스 노드 엔티티에 대한 데이터베이스 접근을 담당하는 JpaRepository.
 */
@Repository
public interface BaseNodeRepository extends JpaRepository<BaseNode, Long> {
    List<BaseNode> findByUser(User user);
    List<BaseNode> findByBaseLine_IdOrderByAgeYearAscIdAsc(Long baseLineId);
    @Modifying
    @Transactional
    @Query("update BaseNode b set b.altOpt1TargetDecisionId = :targetId where b.id = :id and b.altOpt1TargetDecisionId is null")
    int linkAlt1IfEmpty(@Param("id") Long id, @Param("targetId") Long targetId);

    @Modifying
    @Transactional
    @Query("update BaseNode b set b.altOpt2TargetDecisionId = :targetId where b.id = :id and b.altOpt2TargetDecisionId is null")
    int linkAlt2IfEmpty(@Param("id") Long id, @Param("targetId") Long targetId);

    @Modifying
    @Transactional
    @Query("update BaseNode b set b.altOpt1TargetDecisionId = null " +
            "where b.id = :id and b.altOpt1TargetDecisionId = :targetId")
    int unlinkAlt1IfMatches(@Param("id") Long id, @Param("targetId") Long targetId);

    @Modifying
    @Transactional
    @Query("update BaseNode b set b.altOpt2TargetDecisionId = null " +
            "where b.id = :id and b.altOpt2TargetDecisionId = :targetId")
    int unlinkAlt2IfMatches(@Param("id") Long id, @Param("targetId") Long targetId);

    void deleteByBaseLine_Id(Long baseLineId);
}