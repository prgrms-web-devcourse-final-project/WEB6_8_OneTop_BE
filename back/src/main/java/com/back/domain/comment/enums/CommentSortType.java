package com.back.domain.comment.enums;

import lombok.Getter;

/**
 * 최신순, 좋아요순
 */
@Getter
public enum CommentSortType {
    LATEST("createdDate"),
    LIKES("likeCount");

    private final String property;

    CommentSortType(String property) {
        this.property = property;
    }
}
