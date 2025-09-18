package com.back.domain.session.repository;

import com.back.domain.session.entity.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 세션 엔티티에 대한 데이터베이스 접근을 담당하는 JpaRepository.
 */
@Repository
public interface SessionRepository extends JpaRepository<Session, Long> {
}