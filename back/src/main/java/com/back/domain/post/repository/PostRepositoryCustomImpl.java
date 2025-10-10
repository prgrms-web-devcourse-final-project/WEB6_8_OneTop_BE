package com.back.domain.post.repository;

import com.back.domain.post.dto.PostSearchCondition;
import com.back.domain.post.entity.Post;
import com.back.domain.post.enums.PostCategory;
import com.back.domain.post.enums.SearchType;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.PathBuilder;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;

import static com.back.domain.post.entity.QPost.post;
import static com.back.domain.user.entity.QUser.user;

@RequiredArgsConstructor
@Repository
public class PostRepositoryCustomImpl implements PostRepositoryCustom {
    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Post> searchPosts(PostSearchCondition condition, Pageable pageable) {
        List<Post> posts = queryFactory
                .selectFrom(post)
                .leftJoin(post.user, user).fetchJoin()
                .where(getCategoryCondition(condition.category()),
                        getSearchCondition(condition.keyword(), condition.searchType()),
                        excludeHiddenIfSearch(condition.keyword(), condition.searchType()))
                .orderBy(toOrderSpecifier(pageable))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> count = queryFactory
                .select(post.count())
                .from(post)
                .where(
                        getCategoryCondition(condition.category()),
                        getSearchCondition(condition.keyword(), condition.searchType()),
                        excludeHiddenIfSearch(condition.keyword(), condition.searchType())
                );

        return PageableExecutionUtils.getPage(posts, pageable, count::fetchOne);
    }

    /**
     * 1차 필터링 (CHAT, SCENARIO, POLL)
     * category 조건이 null이 아니면 필터링 조건 추가
     */
    private BooleanExpression getCategoryCondition(PostCategory category) {
        return category != null ? post.category.eq(category) : null;
    }

    /**
     * 2차 필터링 (TITLE, TITLE_CONTENT, AUTHOR)
     * fixme 현재 like 기반 검색 - 성능 최적화를 위해 추후에 수정 예정
     */
    private BooleanExpression getSearchCondition(String searchKeyword, SearchType searchType) {
        if (!StringUtils.hasText(searchKeyword) || searchType == null) {
            return null;
        }

        return switch (searchType) {
            case TITLE -> post.title.containsIgnoreCase(searchKeyword);
            case TITLE_CONTENT -> post.title.containsIgnoreCase(searchKeyword)
                    .or(post.content.containsIgnoreCase(searchKeyword));
            case AUTHOR -> post.user.nickname.containsIgnoreCase(searchKeyword);
        };
    }

    private BooleanExpression excludeHiddenIfSearch(String searchKeyword, SearchType searchType) {
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
