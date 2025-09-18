package com.back.domain.like.controller;

import com.back.domain.like.service.LikeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

/**
 * 좋아요 관련 API 요청을 처리하는 컨트롤러.
 */
@RestController
@RequiredArgsConstructor
public class LikeController {

    private final LikeService likeService;

}