package com.back.global.ai.prompt;

import com.back.domain.node.entity.DecisionNode;
import com.back.global.ai.exception.AiServiceException;
import com.back.global.exception.ErrorCode;
import java.util.List;

/**
 * 상황 생성을 위한 프롬프트 템플릿
 * Trees 도메인에서 사용되며, 이전 선택들의 나비효과로 새로운 상황을 생성한다.
 */
public class SituationPrompt {

    private static final String PROMPT_TEMPLATE = """
        당신은 인생 시뮬레이션 전문가입니다. 사용자의 이전 선택들이 만들어낸 나비효과를 분석하여, 새로운 상황을 생성해주세요.

        ## 이전 선택 경로
        {previousChoices}

        ## 시간 기준점
        {timeContext}

        ## 요구사항
        이전 선택들의 자연스러운 결과로 **현재 시점의 베이스 상황과 같은 연도**에 발생할 수 있는 새로운 상황을 **한 문장**으로 생성해주세요.

        ### 작성 가이드라인
        1. **연관성**: 이전 선택들과 논리적으로 연결되는 상황
        2. **현실성**: 실제로 발생 가능한 합리적인 상황
        3. **선택지**: 사용자가 다시 선택해야 하는 분기점이 포함된 상황
        4. **구체성**: 추상적이지 않은 구체적이고 실질적인 상황
        5. **중립성**: 지나치게 긍정적이거나 부정적이지 않은 균형잡힌 상황
        6. **시간 일치**: 해당 연도의 베이스 상황과 동일한 시점에서 발생하는 상황

        ### 연도별 연속 상황 예시

        **2025년 상황 (대학교 4학년 시점)**
        - "대학원 진학 1년 후, 지도교수와의 연구 방향 차이로 갈등이 생겼습니다."
        - "창업 6개월 후, 초기 투자금이 부족해지면서 추가 투자 유치가 필요한 상황입니다."
        - "이직 3개월 후, 새 회사에서 예상보다 업무 강도가 높아 워라밸을 고민하게 되었습니다."

        **2026년 상황 (취업 1년차 시점)**
        - "해외 취업 8개월 후, 현지 문화 적응 어려움으로 한국 복귀를 고려하게 되었습니다."
        - "프리랜서 전환 1년 후, 안정적 수입원 확보를 위한 새로운 전략이 필요한 시점입니다."
        - "첫 직장 생활 중, 부서 이동 기회와 함께 새로운 도전이 필요한 상황입니다."

        **2027년 상황 (취업 2년차 시점)**
        - "결혼 준비 중, 배우자와 미래 거주지에 대한 의견 차이가 발생했습니다."
        - "부모님 사업 승계 제안을 받았지만, 현재 직장에서의 성장 가능성도 높은 상황입니다."
        - "취업 2년 후, 목표했던 승진 기회와 새로운 도전 사이에서 고민 중입니다."

        ### 시간 표현 가이드
        - "~년 후", "~개월 후" 표현으로 이전 선택으로부터의 시간 경과 명시
        - 하지만 전체적으로는 해당 연도의 베이스 상황과 같은 시점

        ### 응답 형식
        반드시 다음 JSON 형식으로 응답해주세요:
        ```json
        {
          "situation": "새로운 상황을 한 문장으로 작성",
          "recommendedOption": "추천 선택지 (15자 이내)"
        }
        ```

        ### 추천 선택지 작성 가이드라인
        1. **간결성**: 15자 이내로 작성
        2. **명확성**: 선택의 방향이 명확하게 드러나는 표현
        3. **현실성**: 해당 상황에서 실제로 선택 가능한 옵션
        4. **UX 개선**: 사용자가 쉽게 이해하고 선택할 수 있는 표현
        5. **균형성**: 지나치게 극단적이지 않은 합리적인 선택지

        ### 추천 선택지 예시
        **"대학원 진학 후 지도교수와 연구 방향 갈등 상황"** → "교수와 직접 대화"
        **"창업 후 자금 부족 상황"** → "추가 투자 유치"
        **"해외 취업 후 문화 적응 어려움"** → "현지 커뮤니티 참여"
        **"승진 기회와 스카우트 제안 동시 상황"** → "현재 회사 승진"
        **"부모 사업 승계 제안 상황"** → "단계별 승계 논의"
        """;

    private static final String INITIAL_SITUATION_TEMPLATE = """
        당신은 인생 시뮬레이션 전문가입니다. 사용자가 처음으로 대안적 선택을 시작하는 시점의 상황을 생성해주세요.

        ## 베이스 상황 정보
        {baseContext}

        ## 시간 기준점
        {timeContext}

        ## 요구사항
        **반드시 베이스 상황과 같은 시점**에서 발생할 수 있는 새로운 도전이나 변화 상황을 **한 문장**으로 생성해주세요.

        ### 작성 가이드라인
        1. **동일 시점**: 베이스 상황과 정확히 같은 연도/시점에서 발생하는 상황
        2. **선택 필요**: 사용자가 결정을 내려야 하는 명확한 분기점
        3. **현실성**: 실제로 발생할 수 있는 자연스러운 상황
        4. **기회성**: 긍정적인 변화의 가능성을 내포한 상황
        5. **구체성**: 모호하지 않은 구체적인 상황 설정
        6. **시간 일치**: "~후", "~년 차" 같은 시간 경과 표현 금지

        ### 동일 시점 상황 예시
        - "대학교 3학년 재학 중, 해외 교환학생 프로그램 지원 기회가 생겼습니다."
        - "현재 회사에서 새로운 부서로의 이동 제안을 받았습니다."
        - "안정적인 직장 생활 중, 평소 관심있던 분야의 창업 동료를 만나게 되었습니다."
        - "대학 졸업을 앞두고, 대학원 진학과 취업 사이에서 고민하게 되었습니다."
        - "현재 직장에서 해외 지사 파견 근무 기회가 주어졌습니다."
        - "현재 거주 중인 도시에서 새로운 도시로의 이주를 검토하게 되었습니다."
        - "부모님으로부터 가업 승계에 대한 진지한 제안을 받았습니다."
        - "현재 직장에서 승진 기회와 동시에 경쟁 회사의 스카우트 제안을 받았습니다."

        ### 응답 형식
        반드시 다음 JSON 형식으로 응답해주세요:
        ```json
        {
          "situation": "새로운 상황을 한 문장으로 작성",
          "recommendedOption": "추천 선택지 (15자 이내)"
        }
        ```

        ### 추천 선택지 작성 가이드라인
        1. **간결성**: 15자 이내로 작성
        2. **명확성**: 선택의 방향이 명확하게 드러나는 표현
        3. **현실성**: 해당 상황에서 실제로 선택 가능한 옵션
        4. **UX 개선**: 사용자가 쉽게 이해하고 선택할 수 있는 표현
        5. **균형성**: 지나치게 극단적이지 않은 합리적인 선택지

        ### 추천 선택지 예시
        **"해외 교환학생 프로그램 지원 기회"** → "교환학생 지원"
        **"새로운 부서 이동 제안"** → "부서 이동 수락"
        **"창업 동료와의 만남"** → "창업 아이템 논의"
        **"해외 지사 파견 근무 기회"** → "파견 근무 지원"
        **"가업 승계 제안"** → "승계 계획 검토"
        """;

    /**
     * 이전 선택들을 기반으로 새로운 상황 생성 프롬프트를 생성한다.
     *
     * @param previousNodes 이전 선택 노드들 (시간순 정렬되어 있어야 함)
     * @return 완성된 프롬프트 문자열
     */
    public static String generatePrompt(List<DecisionNode> previousNodes) {
        return generatePrompt(previousNodes, null);
    }

    /**
     * 이전 선택들과 시간 컨텍스트를 기반으로 새로운 상황 생성 프롬프트를 생성한다.
     *
     * @param previousNodes 이전 선택 노드들 (시간순 정렬되어 있어야 함)
     * @param timeContext 현재 시점의 베이스 상황 정보 (예: "2025년 대학교 4학년 시점")
     * @return 완성된 프롬프트 문자열
     */
    public static String generatePrompt(List<DecisionNode> previousNodes, String timeContext) {
        if (previousNodes == null || previousNodes.isEmpty()) {
            throw new AiServiceException(ErrorCode.AI_INVALID_REQUEST, "Previous nodes cannot be null or empty for situation generation");
        }

        StringBuilder choicesInfo = new StringBuilder();

        // 사용자 출생연도 계산 (모든 노드가 같은 사용자이므로 첫 번째에서 가져옴)
        int birthYear = previousNodes.get(0).getUser().getBirthdayAt().getYear();

        for (int i = 0; i < previousNodes.size(); i++) {
            DecisionNode node = previousNodes.get(i);
            int actualYear = birthYear + node.getAgeYear() - 1; // 실제 연도 계산

            choicesInfo.append(String.format(
                "%d단계 (%d세, %d년):\n상황: %s\n선택: %s\n\n",
                i + 1,
                node.getAgeYear(),
                actualYear,
                node.getSituation() != null ? node.getSituation() : "상황 정보 없음",
                node.getDecision() != null ? node.getDecision() : "선택 정보 없음"
            ));
        }

        String timeContextValue;
        if (timeContext == null || timeContext.trim().isEmpty()) {
            timeContextValue = "현재 시점의 베이스 상황과 같은 연도에서 상황을 생성해주세요.";
        } else {
            timeContextValue = String.format("현재는 %s입니다. 반드시 이 시점에 맞는 상황을 생성해주세요.", timeContext);
        }

        return PROMPT_TEMPLATE
                .replace("{previousChoices}", choicesInfo.toString())
                .replace("{timeContext}", timeContextValue);
    }

    /**
     * 첫 번째 상황 생성을 위한 프롬프트를 생성한다.
     * BaseNode 정보를 바탕으로 초기 분기점 상황을 생성한다.
     *
     * @param baseContext 베이스 상황 설명 (BaseNode들의 요약)
     * @return 첫 상황 생성용 프롬프트
     */
    public static String generateInitialPrompt(String baseContext) {
        if (baseContext == null || baseContext.trim().isEmpty()) {
            baseContext = "사용자의 현재 삶 상황에 대한 정보가 제공되지 않았습니다.";
        }

        return INITIAL_SITUATION_TEMPLATE
                .replace("{baseContext}", baseContext)
                .replace("{timeContext}", "현재 시점 기준으로 상황을 생성해주세요.");
    }

    /**
     * 베이스 상황과 시간 컨텍스트를 모두 포함한 첫 번째 상황 생성 프롬프트를 생성한다.
     *
     * @param baseContext 베이스 상황 설명 (BaseNode들의 요약)
     * @param timeContext 시간 기준점 (예: "2024년 대학교 3학년 시점")
     * @return 첫 상황 생성용 프롬프트
     */
    public static String generateInitialPrompt(String baseContext, String timeContext) {
        if (baseContext == null || baseContext.trim().isEmpty()) {
            baseContext = "사용자의 현재 삶 상황에 대한 정보가 제공되지 않았습니다.";
        }

        if (timeContext == null || timeContext.trim().isEmpty()) {
            timeContext = "현재 시점 기준으로 상황을 생성해주세요.";
        } else {
            timeContext = String.format("반드시 %s와 동일한 시점에서 발생하는 상황을 생성해주세요.", timeContext);
        }

        return INITIAL_SITUATION_TEMPLATE
                .replace("{baseContext}", baseContext)
                .replace("{timeContext}", timeContext);
    }

    /**
     * 이전 선택들의 유효성을 검증한다.
     *
     * @param previousNodes 검증할 이전 선택들
     * @return 검증 결과 (true: 유효, false: 무효)
     */
    public static boolean validatePreviousNodes(List<DecisionNode> previousNodes) {
        if (previousNodes == null || previousNodes.isEmpty()) {
            return false;
        }

        // 모든 노드가 situation과 decision을 가지고 있는지 확인
        return previousNodes.stream()
                .allMatch(node ->
                    node.getSituation() != null && !node.getSituation().trim().isEmpty() &&
                    node.getDecision() != null && !node.getDecision().trim().isEmpty()
                );
    }

    /**
     * 예상 토큰 수를 계산한다.
     * 상황 생성은 상대적으로 짧은 응답을 요구하므로 토큰 수가 적다.
     *
     * @param previousNodes 토큰 수 계산할 이전 선택들
     * @return 예상 토큰 수
     */
    public static int estimateTokens(List<DecisionNode> previousNodes) {
        int baseTokens = 300; // 기본 프롬프트 토큰 수

        if (previousNodes != null) {
            // DecisionNode당 약 60토큰 추가 (상황 + 선택 + 날짜)
            baseTokens += previousNodes.size() * 60;
        }

        return baseTokens;
    }

    /**
     * 첫 상황 생성의 예상 토큰 수를 계산한다.
     *
     * @param baseContext 베이스 컨텍스트 길이
     * @return 예상 토큰 수
     */
    public static int estimateInitialTokens(String baseContext) {
        int baseTokens = 250; // 기본 프롬프트 토큰 수

        if (baseContext != null) {
            // baseContext 길이에 따른 토큰 추가 (대략 4자당 1토큰)
            baseTokens += baseContext.length() / 4;
        }

        return baseTokens;
    }

    /**
     * 프롬프트 응답에서 실제 상황 텍스트만 추출한다.
     * JSON 형식 응답에서 situation 필드를 파싱한다.
     *
     * @param aiResponse AI의 전체 응답
     * @return 상황 텍스트만 추출된 결과
     */
    public static String extractSituation(String aiResponse) {
        if (aiResponse == null || aiResponse.trim().isEmpty()) {
            return "상황 생성에 실패했습니다.";
        }

        String response = aiResponse.trim();

        try {
            // JSON 형식으로 파싱 시도
            if (response.contains("\"situation\"")) {
                int situationStart = response.indexOf("\"situation\"");
                int valueStart = response.indexOf(":", situationStart);
                int valueEnd = response.indexOf(",", valueStart);
                if (valueEnd == -1) {
                    valueEnd = response.indexOf("}", valueStart);
                }

                if (valueStart != -1 && valueEnd != -1) {
                    String situationValue = response.substring(valueStart + 1, valueEnd).trim();
                    // 따옴표 제거
                    situationValue = situationValue.replaceAll("^\"|\"$", "");
                    return situationValue;
                }
            }
        } catch (Exception e) {
            // JSON 파싱 실패 시 기존 방식으로 fallback
        }

        // 기존 방식으로 fallback
        // "상황:" 이후의 텍스트 추출
        int situationIndex = response.indexOf("상황:");
        if (situationIndex != -1) {
            return response.substring(situationIndex + 3).trim();
        }

        // 상황 정보 없이 바로 상황이 나온 경우
        return response;
    }

    /**
     * 프롬프트 응답에서 추천 선택지를 추출한다.
     * JSON 형식 응답에서 recommendedOption 필드를 파싱한다.
     *
     * @param aiResponse AI의 전체 응답
     * @return 추천 선택지 텍스트, 추출 실패 시 null
     */
    public static String extractRecommendedOption(String aiResponse) {
        if (aiResponse == null || aiResponse.trim().isEmpty()) {
            return null;
        }

        String response = aiResponse.trim();

        try {
            // JSON 형식으로 파싱 시도
            if (response.contains("\"recommendedOption\"")) {
                int optionStart = response.indexOf("\"recommendedOption\"");
                int valueStart = response.indexOf(":", optionStart);
                int valueEnd = response.indexOf(",", valueStart);
                if (valueEnd == -1) {
                    valueEnd = response.indexOf("}", valueStart);
                }

                if (valueStart != -1 && valueEnd != -1) {
                    String optionValue = response.substring(valueStart + 1, valueEnd).trim();
                    // 따옴표 제거
                    optionValue = optionValue.replaceAll("^\"|\"$", "");
                    return optionValue.isEmpty() ? null : optionValue;
                }
            }
        } catch (Exception e) {
            // JSON 파싱 실패 시 null 반환
            return null;
        }

        return null;
    }
}