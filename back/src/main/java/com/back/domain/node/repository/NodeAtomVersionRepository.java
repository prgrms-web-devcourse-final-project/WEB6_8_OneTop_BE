/**
 * [REPOSITORY] NodeAtomVersionRepository
 * - 노드 콘텐츠의 불변 버전 스냅샷을 조회/탐색하기 위한 저장소
 * - Atom 기준 최신 버전 조회 및 해시 기반 중복 체크 지원
 */
package com.back.domain.node.repository;

import com.back.domain.node.entity.NodeAtomVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NodeAtomVersionRepository extends JpaRepository<NodeAtomVersion, Long> {

    // Atom 기준 전체 버전 이력 조회(오름차순)
    List<NodeAtomVersion> findByAtom_IdOrderByIdAsc(Long atomId);

    // Atom 기준 최신 버전 조회
    Optional<NodeAtomVersion> findTopByAtom_IdOrderByIdDesc(Long atomId);

    // 콘텐츠 해시로 중복 버전 탐지
    Optional<NodeAtomVersion> findByContentHash(String contentHash);
}
