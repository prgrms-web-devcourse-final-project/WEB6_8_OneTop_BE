package com.back.domain.post.enums;

import lombok.Getter;

@Getter
public enum PostSortType {
    LATEST("createdDate"),
    LIKES("likeCount");

    private final String property;

    PostSortType(String property) {
        this.property = property;
    }
}