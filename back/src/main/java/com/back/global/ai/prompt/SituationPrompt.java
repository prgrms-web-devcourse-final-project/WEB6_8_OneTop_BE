package com.back.global.ai.prompt;

import com.back.domain.node.entity.DecisionNode;
import com.back.global.ai.exception.AiServiceException;
import com.back.global.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

/**
 * 상황 생성을 위한 프롬프트 템플릿
 * Trees 도메인에서 사용되며, 이전 선택들의 나비효과로 새로운 상황을 생성한다.
 */
public class SituationPrompt {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String PROMPT_TEMPLATE = """
        당신은 인생 시뮬레이션 전문가입니다. 이전 선택들의 나비효과로 발생한 새로운 상황을 생성하세요.

        ## 이전 선택 경로
        {previousChoices}

        ## 시간 기준점
        {timeContext}

        ## 요구사항
        이전 선택의 결과로 **해당 연도**에 발생하는 선택이 필요한 상황을 **한 문장**으로 작성하세요.

        ### 작성 규칙
        1. 이전 선택과 논리적으로 연결
        2. 현실적이고 구체적인 상황
        3. 사용자가 결정해야 하는 분기점 포함
        4. "~년 후", "~개월 후" 표현으로 시간 경과 명시

        ### 예시
        - "대학원 진학 1년 후, 지도교수와 연구 방향 차이로 갈등이 생겼습니다."
        - "창업 6개월 후, 초기 투자금 부족으로 추가 투자 유치가 필요합니다."
        - "해외 취업 8개월 후, 현지 문화 적응 어려움으로 복귀를 고려하게 되었습니다."

        ### 응답 형식 (JSON)
        ```json
        {
          "situation": "상황 한 문장",
          "recommendedOption": "추천 선택 (15자 이내)"
        }
        ```
        """;

    private static final String INITIAL_SITUATION_TEMPLATE = """
        당신은 인생 시뮬레이션 전문가입니다. 첫 번째 대안 선택 시점의 상황을 생성하세요.

        ## 베이스 상황 정보
        {baseContext}

        ## 시간 기준점
        {timeContext}

        ## 요구사항
        **베이스 상황과 동일한 시점**에 발생하는 선택 가능한 기회를 **한 문장**으로 작성하세요.

        ### 작성 규칙
        1. 베이스 상황과 같은 연도/시점
        2. 명확한 선택 분기점 포함
        3. 현실적이고 구체적인 기회
        4. 시간 경과 표현("~후", "~년 차") 금지

        ### 예시 (3개만)
        - "대학교 3학년 재학 중, 해외 교환학생 프로그램 지원 기회가 생겼습니다."
        - "현재 회사에서 해외 지사 파견 근무 기회가 주어졌습니다."
        - "대학 졸업을 앞두고, 대학원 진학과 취업 사이에서 고민하게 되었습니다."

        ### 응답 형식 (JSON)
        ```json
        {
          "situation": "상황 한 문장",
          "recommendedOption": "추천 선택 (15자 이내)"
        }
        ```
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

        // 1단계: 전체 경로 요약 (4개 이상일 때만)
        if (previousNodes.size() > 3) {
            choicesInfo.append("전체 선택 경로: ");
            for (int i = 0; i < previousNodes.size(); i++) {
                DecisionNode node = previousNodes.get(i);
                String decision = node.getDecision() != null ? node.getDecision() : "선택없음";

                // 15자로 제한 (12자 + "...")
                if (decision.length() > 15) {
                    decision = decision.substring(0, 12) + "...";
                }

                choicesInfo.append(String.format("%d세 %s", node.getAgeYear(), decision));
                if (i < previousNodes.size() - 1) {
                    choicesInfo.append(" → ");
                }
            }
            choicesInfo.append("\n\n");
        }

        // 2단계: 최근 3개 상세 정보
        int startIndex = Math.max(0, previousNodes.size() - 3);
        if (previousNodes.size() > 3) {
            choicesInfo.append("최근 상세 선택:\n");
        }

        for (int i = startIndex; i < previousNodes.size(); i++) {
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
            if (previousNodes.size() <= 3) {
                // 3개 이하: 전체 상세 전송 (노드당 60토큰)
                baseTokens += previousNodes.size() * 60;
            } else {
                // 4개 이상: 요약(노드당 5토큰) + 최근 3개 상세(60토큰)
                int summaryTokens = previousNodes.size() * 5; // 전체 요약
                int detailTokens = 3 * 60; // 최근 3개 상세
                baseTokens += summaryTokens + detailTokens;
            }
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
            // ObjectMapper를 사용한 JSON 파싱
            JsonNode rootNode = objectMapper.readTree(response);
            if (rootNode.has("situation")) {
                String situation = rootNode.get("situation").asText();
                if (situation != null && !situation.trim().isEmpty()) {
                    return situation;
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
            // ObjectMapper를 사용한 JSON 파싱
            JsonNode rootNode = objectMapper.readTree(response);
            if (rootNode.has("recommendedOption")) {
                String option = rootNode.get("recommendedOption").asText();
                return (option != null && !option.trim().isEmpty()) ? option : null;
            }
        } catch (Exception e) {
            // JSON 파싱 실패 시 null 반환
            return null;
        }

        return null;
    }
}