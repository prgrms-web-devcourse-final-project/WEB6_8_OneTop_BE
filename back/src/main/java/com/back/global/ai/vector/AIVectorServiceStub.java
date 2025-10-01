/**
 * [요약] 테스트 프로파일에서만 활성화되는 AI 스텁 서비스(외부 호출 없음)
 * - generateNextHint: 고정 문자열/또는 null 반환
 */
package com.back.global.ai.vector;

import com.back.domain.node.entity.DecisionNode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Profile("test")
public class AIVectorServiceStub implements AIVectorService {

    // 다음 입력 힌트(상수 또는 null) 반환
    @Override
    public AiNextHint generateNextHint(Long userId, Long decisionLineId, List<DecisionNode> orderedNodes) {

        return new AiNextHint("테스트-상황(한 문장)", "테스트-추천");
    }
}
