package com.back.domain.node.service;

import com.back.domain.node.repository.BaseLineRepository;
import com.back.domain.node.repository.BaseNodeRepository;
import com.back.domain.node.repository.DecisionLineRepository;
import com.back.domain.node.repository.DecisionNodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 노드(분기) 관련 비즈니스 로직을 처리하는 서비스.
 * 사용자의 삶의 분기점 및 선택에 대한 정보를 관리합니다.
 */
@Service
@RequiredArgsConstructor
public class NodeService {

    private final BaseLineRepository baseLineRepository;
    private final BaseNodeRepository baseNodeRepository;
    private final DecisionLineRepository decisionLineRepository;
    private final DecisionNodeRepository decisionNodeRepository;

}