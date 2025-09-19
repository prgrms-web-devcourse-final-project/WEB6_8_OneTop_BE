package com.back.domain.post.dto;

import com.back.domain.post.enums.PostCategory;

import java.time.LocalDateTime;

/**
 * @param id
 * @param title
 * @param content
 * @param category
 * @param hide
 * @param likeCount
 * @param createdAt
 * fixme @param createdBy 추가 예정
 */
public record PostResponse(
        Long id,
        String title,
        String content,
        PostCategory category,
        boolean hide,
        int likeCount,
        LocalDateTime createdAt
) { }
