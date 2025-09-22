package com.back.domain.post.dto;

import com.back.domain.post.enums.PostCategory;
import com.back.domain.post.enums.SearchType;

/**
 * 검색 조건
 * @param category (CHAT, SCENARIO, POLL)
 * @param searchType (TITLE, TITLE_CONTENT, AUTHOR)
 * @param keyword (검색어)
 */
public record PostSearchCondition(
        PostCategory category,
        SearchType searchType,
        String keyword
) {
}
