package com.back.domain.post.dto;

import com.back.domain.post.enums.PostCategory;

public record PostRequest(
        String title,
        String content,
        PostCategory category
) { }
