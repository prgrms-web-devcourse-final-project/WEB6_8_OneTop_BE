package com.back.global.ai.prompt;

import com.back.domain.node.entity.BaseLine;
import com.back.domain.node.entity.BaseNode;
import com.back.global.ai.exception.AiServiceException;
import com.back.global.exception.ErrorCode;

import java.util.List;

/**
 * 베이스 시나리오 생성을 위한 프롬프트 템플릿
 * BaseLine의 BaseNode 정보를 기반으로 현재 삶 상황에서의 기본 시나리오를 생성한다.
 */
public class BaseScenarioPrompt {

    private static final String PROMPT_TEMPLATE = """
        당신은 인생 시나리오 분석 전문가입니다. 현재 삶을 기반으로 3년 후 시나리오를 생성하세요.

        ## 현재 삶 정보
        사용자 ID: {userId}
        현재 나이: {currentAge}세
        현재 연도: {currentYear}년
        베이스라인: {baselineDescription}

        ## 현재 분기점들
        {baseNodes}

        ## 요구사항 (JSON 형식)
        ```json
        {
            "job": "3년 후 예상 직업 (구체적으로)",
            "summary": "3년 후 삶의 요약 (50자 내외)",
            "description": "3년 후 상세 시나리오 (500-800자)",
            "total": 240~260,
            "baselineTitle": "3년 후 상황 제목 (5-8자)",
            "indicators": [
                {"type": "경제", "point": 40~60, "analysis": "분석 (200자)"},
                {"type": "행복", "point": 40~60, "analysis": "분석 (200자)"},
                {"type": "관계", "point": 40~60, "analysis": "분석 (200자)"},
                {"type": "직업", "point": 40~60, "analysis": "분석 (200자)"},
                {"type": "건강", "point": 40~60, "analysis": "분석 (200자)"}
            ],
            "timelineTitles": {
                 "{timelineYears}": "연도별 제목 (5단어 이내)"
            }
        }
        ```

        ## 작성 규칙
        1. 현실적이고 구체적인 시나리오
        2. 모든 지표 평균 50점 (40~60점 범위)
        3. 균형잡힌 분석 (과도한 긍정/부정 금지)
        4. BaseNode 정보 반영

        ## 예시 (간략)
        - 직업: "중견기업 마케팅팀 과장"
        - 요약: "안정적인 직장생활 속에서 가족과의 시간을 소중히 여기는 삶"
        - 경제(50점): "평균적 연봉 수준으로 안정적이나 자산 증식은 제한적입니다."

        반드시 유효한 JSON 형식으로만 응답하세요.
        """;

    /**
     * BaseLine 정보를 기반으로 베이스 시나리오 생성 프롬프트를 생성한다.
     *
     * @param baseLine 베이스라인 엔티티 (BaseNode들을 포함)
     * @return 완성된 프롬프트 문자열
     */
    public static String generatePrompt(BaseLine baseLine) {
        if (baseLine == null) {
            throw new AiServiceException(ErrorCode.AI_INVALID_REQUEST, "BaseLine cannot be null");
        }

        StringBuilder baseNodesInfo = new StringBuilder();
        List<BaseNode> baseNodes = baseLine.getBaseNodes();

        // BaseNode 유효성 검증
        if (baseNodes == null || baseNodes.isEmpty()) {
            throw new AiServiceException(ErrorCode.AI_INVALID_REQUEST, "BaseNode cannot be null or empty for base scenario generation");
        }

        // 사용자 출생연도 계산
        int birthYear = baseLine.getUser().getBirthdayAt().getYear();
        int scenarioYear = birthYear; // 기본값
            for (int i = 0; i < baseNodes.size(); i++) {
                BaseNode node = baseNodes.get(i);
                int actualYear = birthYear + node.getAgeYear() - 1; // 실제 연도 계산

                baseNodesInfo.append(String.format("%d. 카테고리: %s | 나이: %d세 (%d년) | 상황: %s | 결정: %s\n",
                    i + 1,
                    node.getCategory() != null ? node.getCategory().name() : "없음",
                    node.getAgeYear(),
                    actualYear,
                    node.getSituation() != null ? node.getSituation() : "상황 없음",
                    node.getDecision() != null ? node.getDecision() : "결정 없음"));

                // 가장 최근 노드의 연도를 시나리오 기준 연도로 사용
                if (i == baseNodes.size() - 1) {
                    scenarioYear = actualYear;
                }
            }

        // 사용자의 실제 현재 나이와 현재 연도 계산
        int currentYear = java.time.LocalDate.now().getYear();
        int userCurrentAge = currentYear - birthYear + 1;

        // BaseNode들의 실제 연도들을 타임라인 연도로 사용
        StringBuilder timelineYears = new StringBuilder();
        for (int i = 0; i < baseNodes.size(); i++) {
            BaseNode node = baseNodes.get(i);
            int actualYear = birthYear + node.getAgeYear() - 1;
            if (i > 0) timelineYears.append(", ");
            timelineYears.append('"').append(actualYear).append('"').append(": \"제목 (5단어 이내)\"");
        }

        return PROMPT_TEMPLATE
                .replace("{userId}", String.valueOf(baseLine.getUser().getId()))
                .replace("{currentAge}", String.valueOf(userCurrentAge))
                .replace("{currentYear}", String.valueOf(currentYear))
                .replace("{baselineDescription}",
                    baseLine.getTitle() != null ? baseLine.getTitle() : "베이스라인 제목 없음")
                .replace("{baseNodes}", baseNodesInfo.toString())
                .replace("{timelineYears}", timelineYears.toString());
    }
}