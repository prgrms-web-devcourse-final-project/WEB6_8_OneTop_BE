/**
 * [REPOSITORY] BaselinePatchRepository
 * - 커밋 내 ageYear 단위의 버전 교체 기록을 조회하기 위한 저장소
 * - 특정 커밋 집합과 ageYear로 최신 패치를 빠르게 선택 가능
 */
package com.back.domain.node.repository;

import com.back.domain.node.entity.BaselinePatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface BaselinePatchRepository extends JpaRepository<BaselinePatch, Long> {

    // 커밋 단위 패치 목록 조회(오래된 순)
    List<BaselinePatch> findByCommit_IdOrderByIdAsc(Long commitId);


    // 커밋 집합과 ageYear로 필터링하여 최신 우선 정렬
    List<BaselinePatch> findByCommit_IdInAndAgeYearOrderByIdDesc(Collection<Long> commitIds, Integer ageYear);
}
