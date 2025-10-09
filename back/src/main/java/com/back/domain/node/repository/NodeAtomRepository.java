/**
 * [REPOSITORY] NodeAtomRepository
 * - 노드 콘텐츠의 원천 단위를 조회/탐색하기 위한 저장소
 * - contentKey를 활용해 동일 콘텐츠를 재사용하거나 중복 생성을 방지
 */
package com.back.domain.node.repository;

import com.back.domain.node.entity.NodeAtom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NodeAtomRepository extends JpaRepository<NodeAtom, Long> {

    // contentKey로 단일 조회
    Optional<NodeAtom> findByContentKey(String contentKey);
}
