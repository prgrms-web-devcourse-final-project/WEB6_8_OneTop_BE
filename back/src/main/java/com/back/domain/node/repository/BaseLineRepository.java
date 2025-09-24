package com.back.domain.node.repository;

import com.back.domain.node.entity.BaseLine;
import com.back.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
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
}