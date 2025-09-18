package com.back.domain.scenario.repository;

import com.back.domain.scenario.entity.SceneType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 시나리오 유형 엔티티에 대한 데이터베이스 접근을 담당하는 JpaRepository.
 */
@Repository
public interface SceneTypeRepository extends JpaRepository<SceneType, Long> {
}