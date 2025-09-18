package com.back.domain.node.repository;

import com.back.domain.node.entity.BaseLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 베이스라인 엔티티에 대한 데이터베이스 접근을 담당하는 JpaRepository.
 */
@Repository
public interface BaseLineRepository extends JpaRepository<BaseLine, Long> {
}