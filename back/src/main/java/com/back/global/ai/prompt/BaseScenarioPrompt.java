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

        ## 사용자 기본 정보
        사용자 ID: {userId}
        현재 나이: {currentAge}세
        현재 연도: {currentYear}년
        생년월일: {birthday}
        성별: {gender}
        MBTI: {mbti}
        중요시하는 가치관: {beliefs}

        ## 사용자 성향 정보(1~10척도)
        현재 삶 만족도: {lifeSatis}
        현재 관계 만족도: {relationship}
        워라밸 중요도: {workLifeBal}
        위험 회피 성향: {riskAvoid}

        ## 현재 삶 정보
        베이스라인: {baselineDescription}

        ## 과거 주요 인생 기록
        {baseNodes}

        ## 요구사항 (JSON 형식)
        ```json
        {
            "job": "3년 후 예상 직업 (구체적으로)",
            "summary": "3년 후 삶의 요약 (50자 내외)",
            "description": "3년 후 상세 시나리오 (300-500자)",
            "total": 240~260,
            "baselineTitle": "3년 후 상황 제목 (5-8자)",
            "indicators": [
                {"type": "경제", "point": 40~60, "analysis": "분석 (150자)"},
                {"type": "행복", "point": 40~60, "analysis": "분석 (150자)"},
                {"type": "관계", "point": 40~60, "analysis": "분석 (150자)"},
                {"type": "직업", "point": 40~60, "analysis": "분석 (150자)"},
                {"type": "건강", "point": 40~60, "analysis": "분석 (150자)"}
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
        - description: "현재 직장에서 경력을 쌓으며 중간관리자로 성장했습니다. 업무와 개인 생활의 균형을 유지하며, 주말에는 가족과 시간을 보냅니다. 경제적으로 안정적이지만 큰 변화는 없는 평범한 일상이 이어집니다."
        - 경제(50점): "평균적 연봉 수준으로 안정적이나 자산 증식은 제한적입니다. 월급으로 생활하며 저축을 하고 있지만, 큰 투자나 창업 등의 도전은 하지 않아 경제적 성장은 완만합니다."

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

                baseNodesInfo.append(String.format("%d. 카테고리: %s | 나이: %d세 (%d년) | 사건: %s | 결과: %s\n",
                    i + 1,
                    node.getCategory() != null ? node.getCategory().name() : "없음",
                    node.getAgeYear(),
                    actualYear,
                    node.getSituation() != null ? node.getSituation() : "사건 없음",
                    node.getDecision() != null ? node.getDecision() : "결과 없음"));

                // 가장 최근 노드의 연도를 시나리오 기준 연도로 사용
                if (i == baseNodes.size() - 1) {
                    scenarioYear = actualYear;
                }
            }

        // 사용자의 실제 현재 나이와 현재 연도 계산
        int currentYear = java.time.LocalDate.now().getYear();
        int userCurrentAge = currentYear - birthYear + 1;

        // 맨 처음과 맨 끝 노드를 제외한 중간 노드들의 연도만 타임라인에 사용
        StringBuilder timelineYears = new StringBuilder();
        if (baseNodes.size() > 2) {
            java.util.List<BaseNode> intermediateNodes = baseNodes.subList(1, baseNodes.size() - 1);
            for (int i = 0; i < intermediateNodes.size(); i++) {
                BaseNode node = intermediateNodes.get(i);
                int actualYear = birthYear + node.getAgeYear() - 1;
                if (i > 0) {
                    timelineYears.append(", ");
                }
                timelineYears.append('"').append(actualYear).append('"').append(": \"해당 연도 요약 (5단어 이내)\"");
            }
        }

        // 사용자 정보 추출 (null-safe)
        var user = baseLine.getUser();
        String birthday = user.getBirthdayAt() != null ? user.getBirthdayAt().toLocalDate().toString() : "정보 없음";
        String gender = user.getGender() != null ? user.getGender().name() : "정보 없음";
        String mbti = user.getMbti() != null ? user.getMbti().name() : "정보 없음";
        String beliefs = user.getBeliefs() != null && !user.getBeliefs().trim().isEmpty() ? user.getBeliefs() : "정보 없음";

        // 성향 정보 (1-10 척도, null일 수 있음)
        String lifeSatis = user.getLifeSatis() != null ? String.valueOf(user.getLifeSatis()) : "미입력";
        String relationship = user.getRelationship() != null ? String.valueOf(user.getRelationship()) : "미입력";
        String workLifeBal = user.getWorkLifeBal() != null ? String.valueOf(user.getWorkLifeBal()) : "미입력";
        String riskAvoid = user.getRiskAvoid() != null ? String.valueOf(user.getRiskAvoid()) : "미입력";

        return PROMPT_TEMPLATE
                .replace("{userId}", String.valueOf(user.getId()))
                .replace("{currentAge}", String.valueOf(userCurrentAge))
                .replace("{currentYear}", String.valueOf(currentYear))
                .replace("{birthday}", birthday)
                .replace("{gender}", gender)
                .replace("{mbti}", mbti)
                .replace("{lifeSatis}", lifeSatis)
                .replace("{relationship}", relationship)
                .replace("{workLifeBal}", workLifeBal)
                .replace("{riskAvoid}", riskAvoid)
                .replace("{beliefs}", beliefs)
                .replace("{baselineDescription}",
                    baseLine.getTitle() != null ? baseLine.getTitle() : "베이스라인 제목 없음")
                .replace("{baseNodes}", baseNodesInfo.toString())
                .replace("{timelineYears}", timelineYears.toString());
    }

    /**
     * 예상 토큰 수를 계산한다. (로깅 목적)
     * 베이스 시나리오는 중간 크기의 응답을 요구한다.
     *
     * @param baseLine 토큰 수 계산할 베이스라인
     * @return 예상 토큰 수
     */
    public static int estimateTokens(BaseLine baseLine) {
        int baseTokens = 800; // 기본 프롬프트 토큰 수 (사용자 정보 포함)

        if (baseLine != null && baseLine.getBaseNodes() != null) {
            // BaseNode당 약 50토큰 (카테고리, 나이, 상황, 결정 포함)
            baseTokens += baseLine.getBaseNodes().size() * 50;
        }

        return baseTokens;
    }
}