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
 * DecisionLine의 선택 경로와 베이스 시나리오를 비교하여 대안 시나리오를 생성한다.
 */
public class DecisionScenarioPrompt {

    private static final String PROMPT_TEMPLATE = """
        당신은 인생 시나리오 분석 전문가입니다. 사용자의 대안 선택 경로를 분석하여 3년 후 예상 시나리오를 생성하고, 기본 시나리오와 비교 분석해주세요.

        ## 기본 시나리오 (현재 삶 유지 시)
        직업: {baseJob}
        요약: {baseSummary}
        점수 합계: {baseTotal}
        상세 설명: {baseDescription}

        ### 기본 시나리오 지표
        {baseIndicators}

        ## 대안 선택 경로
        {decisionNodes}

        ## 요구사항
        다음 형식으로 JSON 응답을 생성해주세요:

        ```json
        {
            "job": "3년 후 예상 직업 (대안 선택의 결과)",
            "summary": "3년 후 삶의 한 줄 요약 (50자 내외)",
            "description": "3년 후 상세 시나리오 (500-800자, 선택의 나비효과 포함)",
            "total": 275,
            "imagePrompt": "시나리오 이미지 생성용 프롬프트 (영문, 50단어 내외)",
            "indicators": [
                {
                    "type": "경제",
                    "point": 60,
                    "analysis": "경제적 상황 분석 (200자 내외, 기본 시나리오와 차이점 포함)"
                },
                {
                    "type": "행복",
                    "point": 60,
                    "analysis": "행복 지수 분석 (200자 내외, 변화된 점 강조)"
                },
                {
                    "type": "관계",
                    "point": 50,
                    "analysis": "인간관계 변화 분석 (200자 내외)"
                },
                {
                    "type": "직업",
                    "point": 55,
                    "analysis": "직업/커리어 변화 분석 (200자 내외)"
                },
                {
                    "type": "건강",
                    "point": 50,
                    "analysis": "건강 상태 변화 분석 (200자 내외)"
                }
            ],
            "timelineTitles": {
                "1": "1번째 주요 변화 (5단어 이내)",
                "2": "2번째 주요 변화 (5단어 이내)",
                "3": "3번째 주요 변화 (5단어 이내)",
                "4": "4번째 주요 변화 (5단어 이내)",
                "5": "5번째 주요 변화 (5단어 이내)",
                "6": "6번째 주요 변화 (5단어 이내)",
                "7": "7번째 주요 변화 (5단어 이내)",
                "8": "8번째 주요 변화 (5단어 이내)",
                "9": "9번째 주요 변화 (5단어 이내)",
                "10": "10번째 주요 변화 (5단어 이내)"
            },
            "comparisons": [
                {
                    "type": "TOTAL",
                    "baseScore": {baseTotal},
                    "newScore": 300,
                    "analysis": "전체적인 삶의 질 비교 분석 (300자 내외)"
                },
                {
                    "type": "경제",
                    "baseScore": {baseEconomyScore},
                    "newScore": 75,
                    "analysis": "경제적 측면 상세 비교 (300자 내외)"
                },
                {
                    "type": "행복",
                    "baseScore": {baseHappinessScore},
                    "newScore": 85,
                    "analysis": "행복 지수 상세 비교 (300자 내외)"
                },
                {
                    "type": "관계",
                    "baseScore": {baseRelationshipScore},
                    "newScore": 60,
                    "analysis": "인간관계 측면 상세 비교 (300자 내외)"
                },
                {
                    "type": "직업",
                    "baseScore": {baseCareerScore},
                    "newScore": 90,
                    "analysis": "직업/커리어 측면 상세 비교 (300자 내외)"
                },
                {
                    "type": "건강",
                    "baseScore": {baseHealthScore},
                    "newScore": 55,
                    "analysis": "건강 측면 상세 비교 (300자 내외)"
                }
            ]
        }
        ```

        ## 작성 가이드라인
        1. **차별성**: 기본 시나리오와 명확히 구분되는 대안적 결과 제시
        2. **연관성**: 선택 경로가 결과에 미치는 나비효과를 구체적으로 반영
        3. **현실성**: 선택의 결과가 현실적으로 합리적이어야 함
        4. **균형성**: 모든 측면이 좋아지거나 나빠지지 않도록 균형있게 조정
        5. **구체성**: 추상적 표현보다는 구체적이고 실질적인 변화 서술
        6. **비교분석**: 각 지표별로 기본 시나리오와의 차이점을 명확히 제시
        7. **긍정지향**: 전반적으로 긍정적이되, 현실적인 트레이드오프 포함

        ## 작성 예시

        ### 직업 예시 (대안 선택의 결과)
        - "IT 스타트업 공동창업자" (창업 선택 결과)
        - "해외 법인 아시아 지역 총괄" (해외 진출 선택 결과)
        - "독립 컨설턴트 (前 대기업 임원)" (독립 선택 결과)

        ### 요약 예시 (50자 내외)
        - "도전정신으로 새로운 기회를 잡아 성장하며 보람찬 삶을 사는 중"
        - "과감한 선택이 가져온 변화 속에서 더 큰 가능성을 펼치는 일상"
        - "안정보다 성장을 선택해 얻은 성취감과 새로운 도전이 있는 삶"

        ### 지표별 분석 예시 (선택 시나리오별)

        **창업 선택 시나리오 - 3년 후 결과**
        - 경제 (60점): "창업 초기의 불안정함을 극복하고 안정적인 수익 모델을 구축했습니다. 기본 시나리오 대비 수입 변동성은 있지만 성장 가능성이 더 큽니다."
        - 직업 (75점): "대표로서 리더십과 전문성을 크게 향상시키며 업계 내 입지를 다져가고 있습니다. 기본 시나리오보다 성장 속도가 빠릅니다."
        - 건강 (45점): "창업 초기 과로와 스트레스로 체력이 저하되었지만, 점차 업무 분담으로 회복 중입니다. 기본 시나리오보다 건강 관리에 더 신경써야 합니다."
        - 행복 (65점): "성취감과 자아실현으로 높은 만족도를 느끼지만, 불안정성에서 오는 스트레스도 있습니다. 전반적으로는 기본 시나리오보다 보람찬 삶입니다."
        - 관계 (50점): "업무 중심 생활로 기존 관계 유지는 어려워졌으나, 새로운 비즈니스 네트워크를 형성했습니다. 기본 시나리오와 비슷한 수준을 유지합니다."

        **해외 진출 선택 시나리오 - 3년 후 결과**
        - 경제 (65점): "해외 근무 수당과 글로벌 경험 프리미엄으로 기본 연봉보다 높은 수입을 얻고 있습니다. 생활비 증가는 있지만 순소득은 개선되었습니다."
        - 직업 (80점): "글로벌 경험으로 커리어가 급성장하며 국제적 전문성을 인정받고 있습니다. 기본 시나리오 대비 큰 발전을 이뤘습니다."
        - 건강 (55점): "새로운 환경 적응 스트레스는 있지만, 활동적인 라이프스타일로 전반적으로 건강합니다. 기본 시나리오보다 약간 개선된 상태입니다."
        - 행복 (70점): "새로운 도전에서 얻는 만족감과 성장 실감으로 높은 행복감을 느끼고 있습니다. 기본 시나리오보다 활력 넘치는 삶을 살고 있습니다."
        - 관계 (45점): "지리적 거리로 기존 관계 유지가 어렵지만, 현지에서 새로운 국제적 인맥을 쌓고 있습니다. 기본 시나리오보다 다소 아쉬운 부분입니다."

        **대학원 진학 선택 시나리오 - 3년 후 결과**
        - 경제 (45점): "학비 부담과 소득 중단으로 경제적으로는 어려운 상황이지만, 장기적 투자 관점에서 의미있는 선택입니다. 기본 시나리오보다 단기적으로 불리합니다."
        - 직업 (70점): "전문 연구 능력과 학위로 향후 더 높은 수준의 커리어 기회를 확보했습니다. 기본 시나리오보다 전문성이 크게 향상되었습니다."
        - 건강 (50점): "연구 스트레스와 불규칙한 생활패턴이 있지만, 젊은 나이로 큰 무리는 없습니다. 기본 시나리오와 비슷한 수준을 유지합니다."
        - 행복 (55점): "학문적 성취와 미래 가능성에 대한 기대감으로 만족감을 느끼고 있습니다. 기본 시나리오보다 약간 높은 수준입니다."
        - 관계 (60점): "학교 커뮤니티에서 동료들과의 깊이 있는 관계를 형성했습니다. 기본 시나리오보다 의미있는 관계들이 늘어났습니다."

        ### 비교 분석 예시

        **전체 비교 (250점 → 275점)**
        - "기본 시나리오 대비 25점 향상으로, 도전을 통한 성장이 전반적인 삶의 질 개선으로 이어졌습니다. 특히 직업적 성취와 자아실현 측면에서 큰 발전을 이뤘으나, 안정성과 건강 관리 면에서는 주의가 필요합니다."

        **경제 비교 (50점 → 60점)**
        - "기본 시나리오의 안정적이지만 제한적인 소득 대비, 변동성은 있지만 더 큰 성장 가능성을 보여주고 있습니다. 초기 투자와 리스크를 감수한 결과 중장기적으로 더 나은 경제적 기반을 마련할 수 있었습니다."

        ## 점수 가이드라인
        - 총점은 250~350점 범위 (기본: 250점)
        - 개별 지표는 20~100점 범위
        - 선택의 결과에 따라 일부는 상승, 일부는 하락하도록 조정
        - 전체적으로 기본 시나리오보다 상향 조정 (도전의 보상)

        반드시 유효한 JSON 형식으로만 응답해주세요.
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

        if (decisionNodes != null && !decisionNodes.isEmpty()) {
            for (int i = 0; i < decisionNodes.size(); i++) {
                DecisionNode node = decisionNodes.get(i);
                decisionNodesInfo.append(String.format(
                    "%d단계 선택:\n상황: %s\n결정: %s\n선택일자: %s\n\n",
                    i + 1,
                    node.getSituation() != null ? node.getSituation() : "상황 정보 없음",
                    node.getDecision() != null ? node.getDecision() : "결정 정보 없음",
                    node.getCreatedDate() != null ? node.getCreatedDate().toString() : "날짜 없음"
                ));
            }
        } else {
            decisionNodesInfo.append("선택 경로 정보가 없습니다.");
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

        return PROMPT_TEMPLATE
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
                .replace("{baseHealthScore}", String.valueOf(healthScore));
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

    /**
     * 프롬프트 템플릿에서 사용할 수 있는 데이터들을 검증한다.
     *
     * @param decisionLine 검증할 선택 경로
     * @param baseScenario 검증할 베이스 시나리오
     * @return 검증 결과 (true: 유효, false: 무효)
     */
    public static boolean validateInputs(DecisionLine decisionLine, Scenario baseScenario) {
        if (decisionLine == null || baseScenario == null) {
            return false;
        }

        // DecisionNode가 없어도 기본 프롬프트 생성 가능
        return decisionLine.getUser() != null && baseScenario.getUser() != null;
    }

    /**
     * 예상 토큰 수를 계산한다.
     *
     * @param decisionLine 토큰 수 계산할 선택 경로
     * @param baseScenario 베이스 시나리오
     * @return 예상 토큰 수
     */
    public static int estimateTokens(DecisionLine decisionLine, Scenario baseScenario) {
        int baseTokens = 1200; // 기본 프롬프트 토큰 수 (베이스 시나리오 정보 포함)

        if (decisionLine != null && decisionLine.getDecisionNodes() != null) {
            // DecisionNode당 약 80토큰 추가 (상황 + 결정 + 날짜)
            baseTokens += decisionLine.getDecisionNodes().size() * 80;
        }

        return baseTokens;
    }
}