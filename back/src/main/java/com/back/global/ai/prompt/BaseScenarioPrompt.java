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
        당신은 인생 시나리오 분석 전문가입니다. 사용자의 현재 삶 상황을 기반으로 3년 후 예상 시나리오를 생성해주세요.

        ## 현재 삶 정보
        사용자 ID: {userId}
        베이스라인 설명: {baselineDescription}

        ## 현재 분기점들
        {baseNodes}

        ## 요구사항
        다음 형식으로 JSON 응답을 생성해주세요:

        ```json
        {
            "job": "3년 후 예상 직업 (구체적으로)",
            "summary": "3년 후 삶의 한 줄 요약 (50자 내외)",
            "description": "3년 후 상세 시나리오 (500-800자, 구체적이고 현실적으로)",
            "total": 250,
            "baselineTitle": "현재 삶 상황을 5-8자로 요약한 제목",
            "indicators": [
                {
                    "type": "경제",
                    "point": 50,
                    "analysis": "경제적 상황 분석 (200자 내외, 현실적이고 균형잡힌 시각)"
                },
                {
                    "type": "행복",
                    "point": 50,
                    "analysis": "행복 지수 분석 (200자 내외)"
                },
                {
                    "type": "관계",
                    "point": 50,
                    "analysis": "인간관계 상황 분석 (200자 내외)"
                },
                {
                    "type": "직업",
                    "point": 50,
                    "analysis": "직업/커리어 분석 (200자 내외)"
                },
                {
                    "type": "건강",
                    "point": 50,
                    "analysis": "건강 상태 분석 (200자 내외)"
                }
            ],
            "timelineTitles": {
                 "2024": "현재 상황 (5단어 이내)",
                 "2025": "첫 번째 변화 (5단어 이내)",
                 "2026": "중간 발전 (5단어 이내)",
                 "2027": "최종 결과 (5단어 이내)"
            }
        }
        ```

        ## 작성 가이드라인
        1. **현실성**: 현재 상황에서 합리적으로 예상 가능한 시나리오 작성
        2. **균형성**: 모든 지표를 50점(보통 수준)으로 설정하여 중립적 기준선 제공
        3. **구체성**: 추상적 표현보다는 구체적이고 실질적인 내용 포함
        4. **긍정성**: 부정적이지 않은 현실적 시나리오 (절망적이거나 과도하게 낙관적이지 않게)
        5. **연속성**: 타임라인이 논리적으로 연결되도록 구성
        6. **개인화**: 제공된 BaseNode 정보를 충분히 반영

        ## 작성 예시

        ### 직업 예시
        - "중견기업 마케팅팀 과장" (구체적 직급과 부서)
        - "프리랜서 그래픽 디자이너" (구체적 직업군)
        - "중학교 수학 교사" (구체적 교과목과 급별)

        ### 요약 예시 (50자 내외)
        - "안정적인 직장생활 속에서 가족과의 시간을 소중히 여기는 삶"
        - "전문성을 쌓으며 점진적 성장을 추구하는 균형잡힌 일상"
        - "현재 위치에서 꾸준히 발전하며 안정감을 추구하는 생활"

        ### 지표별 분석 예시

        **경제 (50점) 분석 예시**
        - "현재 연봉 수준을 유지하며, 적절한 저축으로 안정적인 경제 기반을 구축했습니다. 특별한 투자 수익은 없지만 생활비 걱정 없이 지낼 수 있는 수준입니다."
        - "월급쟁이의 평균적 소득 수준으로, 큰 부채는 없으나 특별한 자산 증식도 어려운 상태입니다. 기본 생활은 충족되지만 여유자금은 제한적입니다."

        **행복 (50점) 분석 예시**
        - "일과 개인생활의 균형을 어느 정도 유지하고 있으나, 특별한 성취감이나 만족감은 크지 않습니다. 일상의 소소한 즐거움에서 행복을 찾고 있습니다."
        - "현재 상황에 큰 불만은 없지만 특별한 열정이나 목표도 뚜렷하지 않은 상태입니다. 평온하지만 다소 밋밋한 일상을 보내고 있습니다."

        **관계 (50점) 분석 예시**
        - "가족, 친구들과 적당한 거리를 유지하며 관계를 이어가고 있습니다. 깊은 갈등은 없으나 특별히 밀접한 관계도 많지 않은 상태입니다."
        - "직장 동료들과는 업무적으로 원만하고, 개인적 인간관계도 무난한 수준을 유지하고 있습니다. 새로운 만남보다는 기존 관계 유지에 중점을 두고 있습니다."

        **직업 (50점) 분석 예시**
        - "현재 직무에서 평균적인 성과를 보이며 안정적으로 업무를 수행하고 있습니다. 큰 승진은 어렵지만 현 위치를 유지하는 데는 문제없는 상황입니다."
        - "업무 스킬이 어느 정도 안정화되어 특별한 성장은 없지만 실수나 문제도 없는 무난한 직장생활을 이어가고 있습니다."

        **건강 (50점) 분석 예시**
        - "특별한 질병은 없으나 운동 부족과 업무 스트레스로 체력이 예전만 못한 상태입니다. 기본적인 건강관리는 하고 있지만 적극적이지는 않습니다."
        - "정기 건강검진에서 큰 이상은 없으나, 수면 부족과 불규칙한 식습관으로 컨디션 관리가 필요한 시점입니다."

        반드시 유효한 JSON 형식으로만 응답해주세요.
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

        if (baseNodes != null && !baseNodes.isEmpty()) {
            for (int i = 0; i < baseNodes.size(); i++) {
                BaseNode node = baseNodes.get(i);
                baseNodesInfo.append(String.format("%d. 카테고리: %s | 상황: %s | 결정: %s\n",
                    i + 1,
                    node.getCategory() != null ? node.getCategory().name() : "없음",
                    node.getSituation() != null ? node.getSituation() : "상황 없음",
                    node.getDecision() != null ? node.getDecision() : "결정 없음"));
            }
        } else {
            baseNodesInfo.append("현재 분기점 정보가 없습니다.");
        }

        return PROMPT_TEMPLATE
                .replace("{userId}", String.valueOf(baseLine.getUser().getId()))
                .replace("{baselineDescription}",
                    baseLine.getTitle() != null ? baseLine.getTitle() : "베이스라인 제목 없음")
                .replace("{baseNodes}", baseNodesInfo.toString());
    }

    /**
     * 프롬프트 템플릿에서 사용할 수 있는 변수들을 검증한다.
     *
     * @param baseLine 검증할 베이스라인
     * @return 검증 결과 (true: 유효, false: 무효)
     */
    public static boolean validateBaseLine(BaseLine baseLine) {
        if (baseLine == null || baseLine.getUser() == null) {
            return false;
        }

        // BaseNode가 없어도 기본 시나리오 생성 가능
        return true;
    }

    /**
     * 예상 토큰 수를 계산한다.
     * 베이스 프롬프트 + BaseNode 정보를 고려하여 대략적인 토큰 수 반환
     *
     * @param baseLine 토큰 수 계산할 베이스라인
     * @return 예상 토큰 수
     */
    public static int estimateTokens(BaseLine baseLine) {
        int baseTokens = 800; // 기본 프롬프트 토큰 수

        if (baseLine != null && baseLine.getBaseNodes() != null) {
            // BaseNode당 약 50토큰 추가 (카테고리 + 제목 + 내용)
            baseTokens += baseLine.getBaseNodes().size() * 50;
        }

        return baseTokens;
    }
}