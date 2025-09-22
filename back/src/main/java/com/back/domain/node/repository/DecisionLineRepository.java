package com.back.domain.node.repository;

import com.back.domain.node.entity.DecisionLine;
import com.back.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 결정 라인 엔티티에 대한 데이터베이스 접근을 담당하는 JpaRepository.
 */
@Repository
public interface DecisionLineRepository extends JpaRepository<DecisionLine, Long> {
    List<DecisionLine> findByUser(User user);
}