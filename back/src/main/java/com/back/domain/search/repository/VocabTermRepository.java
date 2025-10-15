/*
 * [코드 흐름 요약]
 * - 쿼리 임베딩과 가까운 용어를 유사도(<=>) 순으로 상위 K개 조회한다.
 * - 임베딩은 문자열 리터럴(CAST(:q AS vector))로 전달한다.
 */
package com.back.domain.search.repository;

import com.back.domain.search.entity.VocabTerm;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface VocabTermRepository extends JpaRepository<VocabTerm, Long> {

    @Query(value = """
        SELECT * FROM vocab_term
         ORDER BY embedding <=> CAST(:q AS vector)
         LIMIT :k
        """, nativeQuery = true)
    List<VocabTerm> searchTopK(
            @Param("q") String vectorLiteral,
            @Param("k") int k
    );

    @Query(value = """
        SELECT term FROM vocab_term
         ORDER BY embedding <=> CAST(:q AS vector)
         LIMIT :k
        """, nativeQuery = true)
    List<String> searchTopKTerms(
            @Param("q") String vectorLiteral,
            @Param("k") int k
    );
}
