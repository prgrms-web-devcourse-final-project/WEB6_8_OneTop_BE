package com.back.domain.post.dto;

import com.back.domain.poll.dto.PollOptionResponse;
import com.back.domain.post.enums.PostCategory;
import com.back.global.common.DateFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "게시글 상세 응답 DTO")
public record PostDetailResponse(
        @Schema(description = "게시글 ID", example = "1")
        Long postId,

        @Schema(description = "게시글 제목", example = "테스트 게시글")
        String title,

        @Schema(description = "게시글 내용", example = "게시글 본문 내용")
        String content,

        @Schema(description = "작성자 닉네임 또는 '익명'", example = "홍길동")
        String author,

        @Schema(description = "게시글 카테고리")
        PostCategory category,

        @Schema(description = "좋아요 개수", example = "10")
        int likeCount,

        @Schema(description = "현재 로그인 사용자의 좋아요 여부", example = "true")
        boolean liked,

        @Schema(description = "게시글 작성일자", example = "2025.09.23")
        @DateFormat
        LocalDateTime createdDate,

        @Schema(description = "투표 정보, 투표가 없는 게시글인 경우 null")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        PollOptionResponse polls
) {}

