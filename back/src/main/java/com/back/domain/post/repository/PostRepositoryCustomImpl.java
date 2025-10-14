package com.back.domain.post.repository;

import com.back.domain.post.dto.PostSearchCondition;
import com.back.domain.post.entity.Post;
import com.back.domain.post.enums.PostCategory;
import com.back.domain.post.enums.SearchType;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

import static com.back.domain.post.entity.QPost.post;
import static com.back.domain.user.entity.QUser.user;

@RequiredArgsConstructor
@Repository
public class PostRepositoryCustomImpl implements PostRepositoryCustom {

    private final JPAQueryFactory queryFactory;
    private final EntityManager em;

    @Override
    public Page<Post> searchPosts(PostSearchCondition condition, Pageable pageable) {
        // Full-Text Search가 필요한 경우
        if (isFullTextSearchRequired(condition)) {
            return searchPostsWithFullText(condition, pageable);
        }

        // 일반 검색 (카테고리만 있거나, AUTHOR 검색)
        return searchPostsWithQueryDSL(condition, pageable);
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
        sql.append("SELECT p.* FROM post p ");
        sql.append("LEFT JOIN users u ON p.user_id = u.id ");
        sql.append("WHERE 1=1 ");

        // 카테고리 조건
        if (condition.category() != null) {
            sql.append("AND p.category = :category ");
        }

        // Full-Text Search 조건
        sql.append("AND ").append(getFullTextCondition(condition.searchType())).append(" ");

        // 정렬
        sql.append(buildOrderBy(pageable));

        // 페이징
        sql.append("LIMIT :limit OFFSET :offset");

        return sql.toString();
    }

    /**
     * Full-Text Search 조건 생성
     */
    private String getFullTextCondition(SearchType searchType) {
        return switch (searchType) {
            case TITLE -> "p.search_vector @@ to_tsquery('simple', :tsQuery)";
            case TITLE_CONTENT -> "p.search_vector @@ to_tsquery('simple', :tsQuery)";
            default -> "1=1";
        };
    }

    /**
     * ts_query 문자열 생성 (띄어쓰기 = AND 연산)
     */
    private String buildTsQuery(String keyword) {
        return keyword.trim().replaceAll("\\s+", " & ");
    }

    /**
     * 정렬 조건 생성
     */
    private String buildOrderBy(Pageable pageable) {
        if (pageable.getSort().isEmpty()) {
            return "ORDER BY p.created_date DESC ";
        }

        StringBuilder orderBy = new StringBuilder("ORDER BY ");
        List<String> orders = new ArrayList<>();

        for (Sort.Order order : pageable.getSort()) {
            String column = switch (order.getProperty()) {
                case "createdDate" -> "p.created_date";
                case "likeCount" -> "p.like_count";
                default -> "p.created_date";
            };
            String direction = order.isAscending() ? "ASC" : "DESC";
            orders.add(column + " " + direction);
        }

        orderBy.append(String.join(", ", orders)).append(" ");
        return orderBy.toString();
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

    /**
     * QueryDSL을 사용한 일반 검색 (카테고리만 있거나 AUTHOR 검색)
     */
    private Page<Post> searchPostsWithQueryDSL(PostSearchCondition condition, Pageable pageable) {
        List<Post> posts = queryFactory
                .selectFrom(post)
                .leftJoin(post.user, user).fetchJoin()
                .where(
                        getCategoryCondition(condition.category()),
                        getAuthorSearchCondition(condition.keyword(), condition.searchType()),
                        excludeHiddenIfAuthorSearch(condition.keyword(), condition.searchType())
                )
                .orderBy(toOrderSpecifier(pageable))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(post.count())
                .from(post)
                .where(
                        getCategoryCondition(condition.category()),
                        getAuthorSearchCondition(condition.keyword(), condition.searchType()),
                        excludeHiddenIfAuthorSearch(condition.keyword(), condition.searchType())
                );

        return PageableExecutionUtils.getPage(posts, pageable, countQuery::fetchOne);
    }

    private BooleanExpression getCategoryCondition(PostCategory category) {
        return category != null ? post.category.eq(category) : null;
    }

    private BooleanExpression getAuthorSearchCondition(String searchKeyword, SearchType searchType) {
        if (!StringUtils.hasText(searchKeyword) || searchType != SearchType.AUTHOR) {
            return null;
        }
        return post.user.nickname.containsIgnoreCase(searchKeyword);
    }

    private BooleanExpression excludeHiddenIfAuthorSearch(String searchKeyword, SearchType searchType) {
        if (!StringUtils.hasText(searchKeyword) || searchType != SearchType.AUTHOR) {
            return null;
        }
        return post.hide.eq(false);
    }

    private OrderSpecifier<?>[] toOrderSpecifier(Pageable pageable) {
        if (pageable.getSort().isEmpty()) {
            return new OrderSpecifier[]{post.createdDate.desc()};
        }

        return pageable.getSort().stream()
                .map(order -> {
                    Order direction = order.isAscending() ? Order.ASC : Order.DESC;
                    String property = order.getProperty();
                    return switch (property) {
                        case "createdDate" -> new OrderSpecifier<>(direction, post.createdDate);
                        case "likeCount" -> new OrderSpecifier<>(direction, post.likeCount);
                        default -> new OrderSpecifier<>(direction, post.createdDate);
                    };
                })
                .toArray(OrderSpecifier[]::new);
    }
}