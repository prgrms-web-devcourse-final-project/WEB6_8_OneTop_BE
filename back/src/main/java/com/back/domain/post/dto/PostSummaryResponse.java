package com.back.domain.post.dto;


import com.back.global.common.DateFormat;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 게시글 목록 조회 시 불필요한 정보를 제외한 요약 응답 DTO
 */
@Schema(description = "게시글 요약 응답 DTO")
public record PostSummaryResponse(
        @Schema(description = "게시글 ID", example = "1")
        Long postId,

        @Schema(description = "게시글 제목", example = "테스트 게시글")
        String title,

        @Schema(description = "게시판 타입", example = "잡담")
        String boardType,

        @Schema(description = "작성자 닉네임 또는 익명", example = "홍길동")
        String author,

        @Schema(description = "게시글 작성일자", example = "2025.09.23")
        @DateFormat
        LocalDateTime createdDate,

        @Schema(description = "댓글 개수", example = "5")
        int commentCount,

        @Schema(description = "좋아요 개수", example = "10")
        int likeCount,

        @Schema(description = "현재 로그인 사용자의 해당 게시글 좋아요 여부", example = "true")
        boolean liked
) {}
