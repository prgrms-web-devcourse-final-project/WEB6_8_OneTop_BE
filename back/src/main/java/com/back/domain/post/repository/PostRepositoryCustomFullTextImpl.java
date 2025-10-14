package com.back.domain.post.repository;

import com.back.domain.post.dto.PostSearchCondition;
import com.back.domain.post.entity.Post;
import com.back.domain.post.enums.SearchType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Profile("prod")
@RequiredArgsConstructor
@Repository
public class PostRepositoryCustomFullTextImpl implements PostRepositoryCustom {

    private final EntityManager em;

    @Override
    public Page<Post> searchPosts(PostSearchCondition condition, Pageable pageable) {
        // Full-Text Search가 필요한 경우
        if (isFullTextSearchRequired(condition)) {
            return searchPostsWithFullText(condition, pageable);
        }

        // 일반 검색 (카테고리만 있거나, AUTHOR 검색)
        return searchPostsWithNativeQuery(condition, pageable);
    }

    private Page<Post> searchPostsWithNativeQuery(PostSearchCondition condition, Pageable pageable) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT p.* FROM post p LEFT JOIN users u ON p.user_id = u.id ");

        if (condition.category() != null) {
            sql.append("WHERE p.category = :category ");
        }

        // 정렬 반영
        if (!pageable.getSort().isEmpty()) {
            sql.append("ORDER BY ").append(buildOrderByColumns(pageable));
        } else {
            sql.append("ORDER BY p.created_date DESC");
        }

        sql.append(" LIMIT :limit OFFSET :offset");

        Query query = em.createNativeQuery(sql.toString(), Post.class);

        if (condition.category() != null) {
            query.setParameter("category", condition.category().name());
        }
        query.setParameter("limit", pageable.getPageSize());
        query.setParameter("offset", pageable.getOffset());

        List<Post> posts = query.getResultList();
        long total = countPosts(condition);

        return new PageImpl<>(posts, pageable, total);
    }

    private long countPosts(PostSearchCondition condition) {
        String sql = "SELECT COUNT(*) FROM post p";
        if (condition.category() != null) {
            sql += " WHERE p.category = :category";
        }

        Query query = em.createNativeQuery(sql);

        if (condition.category() != null) {
            query.setParameter("category", condition.category().name());
        }

        return ((Number) query.getSingleResult()).longValue();
    }


    /**
     * 제목, 제목+내용  +  검색어 검색 시 Full-Text Search 적용
     */
    private boolean isFullTextSearchRequired(PostSearchCondition condition) {
        return StringUtils.hasText(condition.keyword())
                && (condition.searchType() == SearchType.TITLE
                || condition.searchType() == SearchType.TITLE_CONTENT);
    }

    /**
     * Native Query로 Full-Text Search 수행
     */
    private Page<Post> searchPostsWithFullText(PostSearchCondition condition, Pageable pageable) {
        String tsQuery = buildTsQuery(condition.keyword());

        // 데이터 조회
        String dataSql = buildDataQuery(condition, pageable);
        Query dataQuery = em.createNativeQuery(dataSql, Post.class);
        setQueryParameters(dataQuery, condition, tsQuery, pageable);

        List<Post> posts = dataQuery.getResultList();

        // 전체 카운트 조회
        long total = countWithFullText(condition, tsQuery);

        return new PageImpl<>(posts, pageable, total);
    }

    /**
     * 데이터 조회 쿼리 생성
     */
    private String buildDataQuery(PostSearchCondition condition, Pageable pageable) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT p.*, ");
        sql.append("ts_rank(p.search_vector, plainto_tsquery('simple', :tsQuery)) as rank ");
        sql.append("FROM post p ");
        sql.append("LEFT JOIN users u ON p.user_id = u.id ");
        sql.append("WHERE ");

        // Full-Text Search 조건
        sql.append(getFullTextCondition(condition.searchType())).append(" ");

        // 카테고리 조건
        if (condition.category() != null) {
            sql.append("AND p.category = :category ");
        }

        // 정렬: rank 우선, 그 다음 사용자 지정 정렬
        sql.append("ORDER BY rank DESC");

        if (!pageable.getSort().isEmpty()) {
            sql.append(", ");
            sql.append(buildOrderByColumns(pageable));
            sql.append(" "); // 공백 추가
        } else {
            sql.append(", p.created_date DESC ");
        }

        // 페이징
        sql.append("LIMIT :limit OFFSET :offset");

        return sql.toString();
    }

    private String buildOrderByColumns(Pageable pageable) {
        // 허용된 정렬 컬럼만 화이트리스트로 관리
        Map<String, String> allowedColumns = Map.of(
                "createdDate", "p.created_date",
                "likeCount", "p.like_count"
        );

        List<String> orders = new ArrayList<>();

        for (Sort.Order order : pageable.getSort()) {
            String property = order.getProperty();

            // 화이트리스트에 없는 컬럼은 무시
            if (!allowedColumns.containsKey(property)) {
                continue; // 또는 예외 발생
            }

            String column = allowedColumns.get(property);

            // direction도 명시적으로 검증
            String direction = order.isAscending() ? "ASC" : "DESC";

            orders.add(column + " " + direction);
        }

        // 정렬 조건이 없으면 기본값 반환
        return orders.isEmpty() ? "p.created_date DESC" : String.join(", ", orders);
    }

    /**
     * Full-Text Search 조건 생성
     */
    private String getFullTextCondition(SearchType searchType) {
        return switch (searchType) {
            case TITLE, TITLE_CONTENT -> "p.search_vector @@ plainto_tsquery('simple', :tsQuery)";
            default -> "1=1";
        };
    }


    /**
     * ts_query 문자열 생성 (띄어쓰기 = AND 연산)
     */
    private String buildTsQuery(String keyword) {
        return keyword.trim();
    }

    /**
     * 쿼리 파라미터 설정
     */
    private void setQueryParameters(Query query, PostSearchCondition condition, String tsQuery, Pageable pageable) {
        if (condition.category() != null) {
            query.setParameter("category", condition.category().name());
        }
        query.setParameter("tsQuery", tsQuery);
        query.setParameter("limit", pageable.getPageSize());
        query.setParameter("offset", pageable.getOffset());
    }

    /**
     * 전체 개수 조회
     */
    private long countWithFullText(PostSearchCondition condition, String tsQuery) {
        // 10,000건 이상이면 "10,000+" 표시
        long maxCount = 10000;

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COUNT(*) FROM (");
        sql.append("  SELECT 1 FROM post p ");
        sql.append("  WHERE 1=1 ");

        if (condition.category() != null) {
            sql.append("  AND p.category = :category ");
        }

        sql.append("  AND ").append(getFullTextCondition(condition.searchType()));
        sql.append("  LIMIT :maxCount");
        sql.append(") subquery");

        Query countQuery = em.createNativeQuery(sql.toString());

        if (condition.category() != null) {
            countQuery.setParameter("category", condition.category().name());
        }
        countQuery.setParameter("tsQuery", tsQuery);
        countQuery.setParameter("maxCount", maxCount);

        return ((Number) countQuery.getSingleResult()).longValue();
    }

}