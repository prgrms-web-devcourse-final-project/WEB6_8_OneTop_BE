package com.back.domain.comment.dto;

import jakarta.validation.constraints.NotBlank;

public record CommentRequest(
        @NotBlank(message = "내용은 필수입니다")
        String content,
        Boolean hide) {
}
