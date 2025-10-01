/*
 * [파일 요약]
 * - 인메모리 유사-벡터 검색과 초경량 프롬프트 생성을 통해
 *   "다음 입력용" AI 힌트(상황 한 문장 + 추천 1개)를 동기 반환한다.
 * - 컨트롤러/엔티티는 건드리지 않고, 서비스 레이어에서만 호출해 DTO 에 주입한다.
 */
package com.back.global.ai.vector;

import com.back.domain.node.entity.DecisionNode;
import java.util.List;

public interface AIVectorService {


    // 다음 입력을 돕는 AI 힌트(상황/추천)를 생성하여 반환
    AiNextHint generateNextHint(Long userId, Long decisionLineId, List<DecisionNode> orderedNodes);

    // 응답 모델: 상황 1문장 + 추천 선택지 1개(15자 이내), 둘 다 null 가능
    record AiNextHint(String aiNextSituation, String aiNextRecommendedOption) {}
}
