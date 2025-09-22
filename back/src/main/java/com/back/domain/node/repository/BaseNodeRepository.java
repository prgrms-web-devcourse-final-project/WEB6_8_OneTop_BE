package com.back.domain.node.repository;

import com.back.domain.node.entity.BaseNode;
import com.back.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 베이스 노드 엔티티에 대한 데이터베이스 접근을 담당하는 JpaRepository.
 */
@Repository
public interface BaseNodeRepository extends JpaRepository<BaseNode, Long> {
    List<BaseNode> findByUser(User user);
    List<BaseNode> findByBaseLine_IdOrderByAgeYearAscIdAsc(Long baseLineId);
}