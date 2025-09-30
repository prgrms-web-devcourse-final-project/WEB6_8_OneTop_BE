package com.back.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 애플리케이션에서 발생하는 다양한 에러 상황을 정의하는 열거형 클래스.
 * 각 에러는 HTTP 상태 코드, 고유한 에러 코드, 그리고 사용자에게 표시될 메시지를 가집니다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common Errors
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "C001", "Invalid Input Value"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "C002", "Method Not Allowed"),
    HANDLE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "C003", "Access is Denied"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C004", "Server Error"),
    INVALID_TYPE_VALUE(HttpStatus.BAD_REQUEST, "C005", "Invalid Type Value"),
    ENTITY_NOT_FOUND(HttpStatus.BAD_REQUEST, "C006", "Entity Not Found"),

    // User Errors
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "U001", "User Not Found"),
    EMAIL_DUPLICATION(HttpStatus.BAD_REQUEST, "U002", "Email Duplication"),
    LOGIN_ID_DUPLICATION(HttpStatus.BAD_REQUEST, "U003", "Login ID Duplication"),
    INVALID_PASSWORD(HttpStatus.BAD_REQUEST, "U004", "Invalid Password"),
    UNAUTHORIZED_USER(HttpStatus.UNAUTHORIZED, "U005", "Unauthorized User"),
    NICKNAME_DUPLICATION(HttpStatus.CONFLICT, "U006", "이미 사용 중인 닉네임입니다."),

    // Post Errors
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "P001", "Post Not Found"),
    POST_ALREADY_LIKED(HttpStatus.BAD_REQUEST, "P002", "Post Already Liked"),

    // Comment Errors
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "CM001", "Comment Not Found"),

    // Session Errors
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "S001", "Session Not Found"),
    SESSION_EXPIRED(HttpStatus.UNAUTHORIZED, "S002", "Session Expired"),
    SESSION_REVOKED(HttpStatus.UNAUTHORIZED, "S003", "Session Revoked"),

    // Node Errors
    NODE_NOT_FOUND(HttpStatus.NOT_FOUND, "N001", "Node Not Found"),
    BASE_LINE_NOT_FOUND(HttpStatus.NOT_FOUND, "N002", "BaseLine Not Found"),
    DECISION_LINE_NOT_FOUND(HttpStatus.NOT_FOUND, "N003", "DecisionLine Not Found"),
    GUEST_BASELINE_LIMIT(HttpStatus.BAD_REQUEST, "N004" , "Guest Base Line Limit Exceeded"),

    // Scenario Errors
    SCENARIO_NOT_FOUND(HttpStatus.NOT_FOUND, "SC001", "Scenario Not Found"),
    SCENARIO_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "SC002", "Scenario Request Not Found"),
    SCENE_COMPARE_NOT_FOUND(HttpStatus.NOT_FOUND, "SC003", "Scene Compare Not Found"),
    SCENE_TYPE_NOT_FOUND(HttpStatus.NOT_FOUND, "SC004", "Scene Type Not Found"),
    SCENARIO_ALREADY_IN_PROGRESS(HttpStatus.CONFLICT, "SC005", "Scenario Already In Progress"),
    BASE_SCENARIO_NOT_FOUND(HttpStatus.NOT_FOUND, "SC006", "Base Scenario Not Found"),
    SCENARIO_TIMELINE_NOT_FOUND(HttpStatus.NOT_FOUND, "SC007", "Scenario Timeline Not Found"),

    // Like Errors
    LIKE_NOT_FOUND(HttpStatus.NOT_FOUND, "L001", "Like Not Found"),

    // Poll Errors
    POLL_VOTE_NOT_FOUND(HttpStatus.NOT_FOUND, "PV001", "Poll Vote Not Found"),
    POLL_VOTE_INVALID_FORMAT(HttpStatus.BAD_REQUEST, "PV002", "투표 형식이 올바르지 않습니다."),
    POLL_VOTE_INVALID_OPTION(HttpStatus.BAD_REQUEST, "PV003", "존재하지 않는 투표 항목입니다." );


    private final HttpStatus status;
    private final String code;
    private final String message;
}
