package com.back.domain.node.controller;

import com.back.domain.node.service.NodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 노드(분기) 관련 API 요청을 처리하는 컨트롤러.
 * 사용자의 삶의 분기점 및 선택에 대한 정보를 관리합니다.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class NodeController {

    private final NodeService nodeService;

}