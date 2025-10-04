package com.back.global.ai.prompt;

import com.back.domain.node.entity.DecisionLine;
import com.back.domain.node.entity.DecisionNode;
import com.back.domain.scenario.entity.Scenario;
import com.back.domain.scenario.entity.SceneType;
import com.back.global.ai.exception.AiServiceException;
import com.back.global.exception.ErrorCode;
import java.util.List;

/**
 * 새로운 시나리오 생성을 위한 프롬프트 템플릿
 * DecisionLine과 베이스 시나리오를 기반으로 대안 시나리오 생성 프롬프트를 제공합니다.
 */
public class DecisionScenarioPrompt {

    private static final String PROMPT_TEMPLATE = """
        당신은 인생 시나리오 분석 전문가입니다. 대안 선택 경로를 분석하여 3년 후 시나리오를 생성하고 기본 시나리오와 비교하세요.

        ## 사용자 정보
        사용자 ID: {userId}
        현재 나이: {currentAge}세
        현재 연도: {currentYear}년

        ## 기본 시나리오 (현재 삶 유지 시)
        직업: {baseJob}
        요약: {baseSummary}
        점수: {baseTotal}점
        설명: {baseDescription}
        지표: {baseIndicators}

        ## 대안 선택 경로
        {decisionNodes}

        ## 요구사항 (JSON 형식)
        ```json
        {
            "job": "3년 후 예상 직업 (대안 선택 결과)",
            "summary": "3년 후 삶의 요약 (50자 내외)",
            "description": "3년 후 상세 시나리오 (500-800자, 나비효과 포함)",
            "total": 250~350,
            "imagePrompt": "이미지 프롬프트 (영문, 50단어)",
            "indicators": [
                {"type": "경제", "point": 30~96, "analysis": "분석 (200자, 기본과 차이점)"},
                {"type": "행복", "point": 30~96, "analysis": "분석 (200자, 변화 강조)"},
                {"type": "관계", "point": 30~96, "analysis": "분석 (200자)"},
                {"type": "직업", "point": 30~96, "analysis": "분석 (200자)"},
                {"type": "건강", "point": 30~96, "analysis": "분석 (200자)"}
            ],
            "timelineTitles": {"{timelineYears}": "연도별 제목 (5단어 이내)"},
            "comparisons": [
                {"type": "TOTAL", "baseScore": {baseTotal}, "newScore": "합계", "analysis": "비교 (300자)"},
                {"type": "경제", "baseScore": {baseEconomyScore}, "newScore": "점수", "analysis": "비교 (300자)"},
                {"type": "행복", "baseScore": {baseHappinessScore}, "newScore": "점수", "analysis": "비교 (300자)"},
                {"type": "관계", "baseScore": {baseRelationshipScore}, "newScore": "점수", "analysis": "비교 (300자)"},
                {"type": "직업", "baseScore": {baseCareerScore}, "newScore": "점수", "analysis": "비교 (300자)"},
                {"type": "건강", "baseScore": {baseHealthScore}, "newScore": "점수", "analysis": "비교 (300자)"}
            ]
        }
        ```

        ## 작성 규칙
        1. 기본 시나리오와 명확히 구분되는 결과
        2. 선택의 나비효과를 구체적으로 반영
        3. 현실적 결과 (일부 상승, 일부 하락)
        4. 전체적으로는 기본보다 향상 (총점 +20~100점)
        5. 75점 초과 시 보수적 적용

        ## 예시 (간략)
        - 창업 선택: 경제(60점), 직업(75점), 건강(45점) - "불안정하나 성장 가능성"
        - 해외 진출: 경제(65점), 직업(80점), 관계(45점) - "글로벌 경험 vs 거리"
        - 대학원: 경제(45점), 직업(70점), 관계(60점) - "단기 손실, 장기 투자"

        반드시 유효한 JSON 형식으로만 응답하세요.
        """;

    /**
     * DecisionLine과 베이스 시나리오를 기반으로 새 시나리오 생성 프롬프트를 생성한다.
     *
     * @param decisionLine 사용자의 선택 경로
     * @param baseScenario 비교 기준이 되는 베이스 시나리오
     * @param baseSceneTypes 베이스 시나리오의 지표들
     * @return 완성된 프롬프트 문자열
     */
    public static String generatePrompt(DecisionLine decisionLine, Scenario baseScenario, List<SceneType> baseSceneTypes) {
        if (decisionLine == null || baseScenario == null) {
            throw new AiServiceException(ErrorCode.AI_INVALID_REQUEST, "DecisionLine and BaseScenario cannot be null");
        }

        // 선택 경로 정보 구성
        StringBuilder decisionNodesInfo = new StringBuilder();
        List<DecisionNode> decisionNodes = decisionLine.getDecisionNodes();

        // DecisionNode 유효성 검증
        if (decisionNodes == null || decisionNodes.isEmpty()) {
            throw new AiServiceException(ErrorCode.AI_INVALID_REQUEST, "DecisionNode cannot be null or empty for decision scenario generation");
        }

        // 사용자 출생연도 계산
        int birthYear = decisionLine.getUser().getBirthdayAt().getYear();
        int currentYear = java.time.LocalDate.now().getYear();
        int userCurrentAge = currentYear - birthYear + 1;

        for (int i = 0; i < decisionNodes.size(); i++) {
            DecisionNode node = decisionNodes.get(i);
            int actualYear = birthYear + node.getAgeYear() - 1; // 실제 연도 계산

            decisionNodesInfo.append(String.format(
                "%d단계 선택 (%d세, %d년):\n상황: %s\n결정: %s\n\n",
                i + 1,
                node.getAgeYear(),
                actualYear,
                node.getSituation() != null ? node.getSituation() : "상황 정보 없음",
                node.getDecision() != null ? node.getDecision() : "결정 정보 없음"
            ));
        }

        // 베이스 시나리오 지표 정보 구성
        StringBuilder baseIndicatorsInfo = new StringBuilder();
        if (baseSceneTypes != null && !baseSceneTypes.isEmpty()) {
            for (SceneType sceneType : baseSceneTypes) {
                baseIndicatorsInfo.append(String.format("- %s: %d점 (%s)\n",
                    sceneType.getType().name(),
                    sceneType.getPoint(),
                    sceneType.getAnalysis() != null ? sceneType.getAnalysis() : "분석 없음"
                ));
            }
        } else {
            baseIndicatorsInfo.append("기본 시나리오 지표 정보가 없습니다.");
        }

        // 지표별 점수 추출
        int economyScore = getScoreByType(baseSceneTypes, "경제");
        int happinessScore = getScoreByType(baseSceneTypes, "행복");
        int relationshipScore = getScoreByType(baseSceneTypes, "관계");
        int careerScore = getScoreByType(baseSceneTypes, "직업");
        int healthScore = getScoreByType(baseSceneTypes, "건강");

        // DecisionNode들의 실제 연도들을 타임라인 연도로 사용
        StringBuilder timelineYears = new StringBuilder();
        for (int i = 0; i < decisionNodes.size(); i++) {
            DecisionNode node = decisionNodes.get(i);
            int actualYear = birthYear + node.getAgeYear() - 1;
            if (i > 0) timelineYears.append(", ");
            timelineYears.append('"').append(actualYear).append('"').append(": \"제목 (5단어 이내)\"");
        }

        return PROMPT_TEMPLATE
                .replace("{userId}", String.valueOf(decisionLine.getUser().getId()))
                .replace("{currentAge}", String.valueOf(userCurrentAge))
                .replace("{currentYear}", String.valueOf(currentYear))
                .replace("{baseJob}", baseScenario.getJob() != null ? baseScenario.getJob() : "직업 정보 없음")
                .replace("{baseSummary}", baseScenario.getSummary() != null ? baseScenario.getSummary() : "요약 없음")
                .replace("{baseTotal}", String.valueOf(baseScenario.getTotal()))
                .replace("{baseDescription}", baseScenario.getDescription() != null ? baseScenario.getDescription() : "설명 없음")
                .replace("{baseIndicators}", baseIndicatorsInfo.toString())
                .replace("{decisionNodes}", decisionNodesInfo.toString())
                .replace("{baseEconomyScore}", String.valueOf(economyScore))
                .replace("{baseHappinessScore}", String.valueOf(happinessScore))
                .replace("{baseRelationshipScore}", String.valueOf(relationshipScore))
                .replace("{baseCareerScore}", String.valueOf(careerScore))
                .replace("{baseHealthScore}", String.valueOf(healthScore))
                .replace("{timelineYears}", timelineYears.toString());
    }

    /**
     * 지표 타입에 해당하는 점수를 찾아 반환한다.
     *
     * @param sceneTypes 지표 목록
     * @param typeName 찾을 지표 타입명
     * @return 해당 타입의 점수 (없으면 50점 기본값)
     */
    private static int getScoreByType(List<SceneType> sceneTypes, String typeName) {
        if (sceneTypes == null) return 50;

        return sceneTypes.stream()
                .filter(sceneType -> sceneType.getType().name().equals(typeName))
                .mapToInt(SceneType::getPoint)
                .findFirst()
                .orElse(50);
    }
}