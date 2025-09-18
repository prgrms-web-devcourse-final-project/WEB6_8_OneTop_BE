package com.back.domain.scenario.service;

import com.back.domain.scenario.repository.ScenarioRepository;
import com.back.domain.scenario.repository.ScenarioRequestRepository;
import com.back.domain.scenario.repository.SceneTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 시나리오 관련 비즈니스 로직을 처리하는 서비스.
 * 시나리오 추출, 상세 조회, 비교 등의 기능을 제공합니다.
 */
@Service
@RequiredArgsConstructor
public class ScenarioService {

    private final ScenarioRequestRepository scenarioRequestRepository;
    private final ScenarioRepository scenarioRepository;
    private final SceneTypeRepository sceneTypeRepository;

}