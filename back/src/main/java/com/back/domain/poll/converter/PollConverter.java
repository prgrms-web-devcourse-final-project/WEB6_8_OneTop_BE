package com.back.domain.poll.converter;

import com.back.domain.poll.dto.PollOptionResponse;
import com.back.domain.poll.dto.PollRequest;
import com.back.domain.poll.dto.PollResponse;
import com.back.domain.poll.dto.VoteRequest;
import com.back.global.exception.ApiException;
import com.back.global.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PollVote, Post 필드 JSON 변환기
 */
@Component
@RequiredArgsConstructor
public class PollConverter {

    private final ObjectMapper objectMapper;

    // json 형태의 문자열 -> PollResponse
    public PollResponse fromPollJson(String voteContent) {
        if (voteContent == null) return null;
        try {
            return objectMapper.readValue(voteContent, PollResponse.class);
        } catch (JsonProcessingException e) {
            throw new ApiException(ErrorCode.POLL_VOTE_INVALID_FORMAT);
        }
    }

    // json 형태의 문자열 -> 리스트
    public List<Integer> fromChoiceJson(String choiceJson) {
        if (choiceJson == null) return List.of();
        try {
            VoteRequest req = objectMapper.readValue(choiceJson, VoteRequest.class);
            return req.choice();
        } catch (JsonProcessingException e) {
            throw new ApiException(ErrorCode.POLL_VOTE_INVALID_FORMAT);
        }
    }

    // [1,2] => "{"choice":[1,2]} 형태로 변환
    public String toChoiceJson(List<Integer> selectedIndexes) {
        try {
            Map<String, Object> map = Map.of("choice", selectedIndexes);
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new ApiException(ErrorCode.POLL_VOTE_INVALID_FORMAT);
        }
    }

    // {"options":[{"index":1,"text":"첫 번째 옵션"},{...}, "pollUid":"UUID"}
    public String toPollContentJson(UUID pollUid, List<PollRequest.PollOption> options) {
        try {
            Map<String, Object> pollMap = new HashMap<>();
            pollMap.put("pollUid", pollUid.toString());
            pollMap.put("options", options);
            return objectMapper.writeValueAsString(pollMap);
        } catch (JsonProcessingException e) {
            throw new ApiException(ErrorCode.POLL_VOTE_INVALID_FORMAT);
        }
    }

    // json 형태의 문자열 -> PollOptionResponse
    public PollOptionResponse fromPollOptionJson(String voteContent) {
        if (voteContent == null) return null;
        try {
            return objectMapper.readValue(voteContent, PollOptionResponse.class);
        } catch (JsonProcessingException e) {
            throw new ApiException(ErrorCode.POLL_VOTE_INVALID_FORMAT);
        }
    }
}