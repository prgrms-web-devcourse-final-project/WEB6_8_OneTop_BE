package com.back.domain.node.repository;

import com.back.domain.node.entity.BaseLine;
import com.back.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 베이스라인 엔티티에 대한 데이터베이스 접근을 담당하는 JpaRepository.
 */
@Repository
public interface BaseLineRepository extends JpaRepository<BaseLine, Long> {
    Optional<BaseLine> findByUser(User user);
    long countByUser(User user); // 기본 인덱스 계산용

    boolean existsByUserAndTitle(User user, String title); // 충돌 회피용

    /**
     * 사용자별 베이스라인 목록 조회 (페이지네이션 지원)
     * BaseNode들과 함께 조회하여 N+1 문제 방지
     */
    @Query("SELECT DISTINCT bl FROM BaseLine bl " +
           "LEFT JOIN FETCH bl.baseNodes bn " +
           "WHERE bl.user.id = :userId " +
           "ORDER BY bl.createdDate DESC")
    Page<BaseLine> findAllByUserIdWithBaseNodes(@Param("userId") Long userId, Pageable pageable);
}