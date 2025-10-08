/**
 * [REPOSITORY] BaselineCommitRepository
 * - 브랜치상의 커밋 이력을 조회/탐색하기 위한 저장소
 * - 최신 커밋 조회 및 브랜치별 커밋 목록 조회 지원
 */
package com.back.domain.node.repository;

import com.back.domain.node.entity.BaselineCommit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BaselineCommitRepository extends JpaRepository<BaselineCommit, Long> {

    // 브랜치 기준 최신 커밋 조회
    Optional<BaselineCommit> findTopByBranch_IdOrderByIdDesc(Long branchId);

    // 브랜치 기준 커밋 목록 조회(최신 우선)
    List<BaselineCommit> findByBranch_IdOrderByIdDesc(Long branchId);

    // N+1 방지용: 부모 커밋 즉시 로딩
    @EntityGraph(attributePaths = {"parentCommit"})
    List<BaselineCommit> findAll();
}
