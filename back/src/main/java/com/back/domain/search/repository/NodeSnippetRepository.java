/*
 * 이 파일은 라인/나이 윈도우로 후보를 좁힌 뒤 pgvector 유사도로 정렬해 topK를 반환하는 네이티브 쿼리를 제공한다.
 */
package com.back.domain.search.repository;

import com.back.domain.search.entity.NodeSnippet;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NodeSnippetRepository extends JpaRepository<NodeSnippet, Long> {

    // 라인/나이 윈도우 필터 + pgvector 유사도(<=>) 정렬로 상위 K를 조회한다.
    @Query(value = """
        SELECT * FROM node_snippet
         WHERE line_id = :lineId
           AND age_year BETWEEN :minAge AND :maxAge
         ORDER BY embedding <=> CAST(:q AS vector)
         LIMIT :k
        """, nativeQuery = true)
    List<NodeSnippet> searchTopKByLineAndAgeWindow(
            @Param("lineId") Long lineId,
            @Param("minAge") Integer minAge,
            @Param("maxAge") Integer maxAge,
            @Param("q") String vectorLiteral,
            @Param("k") int k
    );

    // 텍스트만(가볍게) 가져오기 — 네트워크·파싱 비용 급감
    @Query(value = """
        SELECT text FROM node_snippet
         WHERE line_id = :lineId
           AND age_year BETWEEN :minAge AND :maxAge
         ORDER BY embedding <=> CAST(:q AS vector)
         LIMIT :k
        """, nativeQuery = true)
    List<String> searchTopKTextByLineAndAgeWindow(
            @Param("lineId") Long lineId,
            @Param("minAge") Integer minAge,
            @Param("maxAge") Integer maxAge,
            @Param("q") String vectorLiteral,
            @Param("k") int k
    );
}
