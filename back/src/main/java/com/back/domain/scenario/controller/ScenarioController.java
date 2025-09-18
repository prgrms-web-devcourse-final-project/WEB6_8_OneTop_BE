package com.back.domain.scenario.controller;

import com.back.domain.scenario.service.ScenarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 시나리오 관련 API 요청을 처리하는 컨트롤러.
 * 시나리오 추출, 상세 조회, 비교 등의 기능을 제공합니다.
 */
@RestController
@RequestMapping("/api/v1/scenarios")
@RequiredArgsConstructor
public class ScenarioController {

    private final ScenarioService scenarioService;

}