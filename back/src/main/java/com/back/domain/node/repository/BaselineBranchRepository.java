/**
 * [REPOSITORY] BaselineBranchRepository
 * - BaseLine 위에서의 브랜치를 조회/탐색하기 위한 저장소
 * - 브랜치명으로 단일 조회 및 라인별 브랜치 목록 조회 지원
 */
package com.back.domain.node.repository;

import com.back.domain.node.entity.BaselineBranch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BaselineBranchRepository extends JpaRepository<BaselineBranch, Long> {

    // BaseLine과 이름으로 단일 조회
    Optional<BaselineBranch> findByBaseLine_IdAndName(Long baseLineId, String name);

    // BaseLine 기준 브랜치 목록 조회
    List<BaselineBranch> findByBaseLine_Id(Long baseLineId);

    void deleteByBaseLine_Id(Long baseLineId);

    @Modifying
    @Query("update BaselineBranch b set b.headCommit = null where b.baseLine.id = :baseLineId")
    void clearHeadByBaseLineId(@Param("baseLineId") Long baseLineId);
}
