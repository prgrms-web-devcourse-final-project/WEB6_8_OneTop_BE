/*
 * [코드 흐름 요약]
 * - age 범위 + (선택) category로 필터하고 pgvector 유사도로 상위 K 테마를 가져온다.
 * - 시더 중복 방지용으로 기존 테마 문자열을 조회하는 메서드를 제공한다.
 */
package com.back.domain.search.repository;

import com.back.domain.node.entity.NodeCategory;
import com.back.domain.search.entity.AgeTheme;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AgeThemeRepository extends JpaRepository<AgeTheme, Long> {

    // next 노드 생성
    @Query(value = """
        SELECT theme FROM age_theme
         WHERE :age BETWEEN min_age AND max_age
           AND (:cat IS NULL OR category = :cat)
         ORDER BY embedding <=> CAST(:q AS vector)
         LIMIT :k
        """, nativeQuery = true)
    List<String> topKThemesByAgeAndCategory(
            @Param("age") int age,
            @Param("cat") String categoryOrNull,    // Enum이면 cat.name()으로 전달
            @Param("q") String vectorLiteral,
            @Param("k") int k
    );

    // 무결성 검증
    long countByMinAge(int minAge);
    long countByMinAgeAndCategory(int minAge, NodeCategory category);

    // 중복 방지용(시더에서 필요할 때만 사용)
    @Query("select a.theme from AgeTheme a where a.minAge = :minAge and a.category = :category")
    List<String> findThemesByMinAgeAndCategory(@Param("minAge") int minAge,
                                               @Param("category") NodeCategory category);
}
