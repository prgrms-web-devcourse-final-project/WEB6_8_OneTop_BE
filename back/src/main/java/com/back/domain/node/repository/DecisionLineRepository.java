package com.back.domain.node.repository;

import com.back.domain.node.entity.DecisionLine;
import com.back.domain.user.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 결정 라인 엔티티에 대한 데이터베이스 접근을 담당하는 JpaRepository.
 */
@Repository
public interface DecisionLineRepository extends JpaRepository<DecisionLine, Long> {
    List<DecisionLine> findByUser(User user);
    List<DecisionLine> findByBaseLine_Id(Long baseLineId);

    @EntityGraph(attributePaths = {"user"})
    Optional<DecisionLine> findWithUserById(Long id);

    @EntityGraph(attributePaths = {"user", "baseLine", "baseLine.baseNodes"})
    Optional<DecisionLine> findWithUserAndBaseLineById(Long id);

    void deleteByBaseLine_Id(Long baseLineId);
}