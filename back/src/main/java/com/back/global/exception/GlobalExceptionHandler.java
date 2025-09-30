/**
 * GlobalExceptionHandler
 * - 도메인/서비스에서 던진 ApiException과 일반 예외를 RFC7807 ProblemDetail로 일관 변환
 * - 바인딩/검증/타입 불일치 등 스프링 표준 예외도 공통 포맷으로 응답
 * - 추가 속성(code, path, timestamp, traceId 등)을 포함해 디버깅과 표준화 동시 달성
 */
package com.back.global.exception;

import com.back.global.problemdetail.ProblemDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ApiException → ProblemDetail
    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApiException(ApiException ex, HttpServletRequest req) {
        // ApiException을 ProblemDetail로 변환
        return ProblemDetails.of(ex.getErrorCode(), req, ex.getMessage());
    }

    // @Valid 바인딩 에러(필드 단위)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpServletRequest req) {
        // 필드 에러 리스트 프로퍼티로 첨부
        var fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of(
                        "field", fe.getField(),
                        "rejectedValue", fe.getRejectedValue(),
                        "message", fe.getDefaultMessage()))
                .toList();

        Map<String, Object> props = new HashMap<>();
        props.put("fieldErrors", fieldErrors);

        // INVALID_INPUT_VALUE 포맷으로 생성
        return ProblemDetails.of(
                ErrorCode.INVALID_INPUT_VALUE.getStatus(),
                ErrorCode.INVALID_INPUT_VALUE.name(),
                ErrorCode.INVALID_INPUT_VALUE.getMessage(),
                ErrorCode.INVALID_INPUT_VALUE.getCode(),
                props,
                req
        );
    }

    // 바인딩/제약조건/메시지 파싱 에러 묶음
    @ExceptionHandler({
            BindException.class,
            ConstraintViolationException.class,
            HttpMessageNotReadableException.class
    })
    public ProblemDetail handleBindingLikeExceptions(Exception ex, HttpServletRequest req) {
        // 상세 메시지 포함하여 INVALID_INPUT_VALUE로 응답
        return ProblemDetails.of(ErrorCode.INVALID_INPUT_VALUE, req, ex.getMessage());
    }

    // 경로/파라미터 타입 불일치 → 400
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        // 가장 많이 나는 타입 오류를 일관 포맷으로
        String detail = "Invalid path or query parameter type: %s".formatted(ex.getName());
        return ProblemDetails.of(
                HttpStatus.BAD_REQUEST,
                ErrorCode.INVALID_TYPE_VALUE.name(),
                detail,
                ErrorCode.INVALID_TYPE_VALUE.getCode(),
                Map.of(
                        "parameter", ex.getName(),
                        "requiredType", ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown",
                        "value", ex.getValue()
                ),
                req
        );
    }

    // IllegalArgument → 400
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest req) {
        // 자주 쓰이는 유효성 실패를 공통 포맷으로
        return ProblemDetails.of(ErrorCode.INVALID_INPUT_VALUE, req, ex.getMessage());
    }

    // 최종 안전망
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleAny(Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception occurred", ex);
        // 예상치 못한 예외도 표준 포맷으로
        var pd = ProblemDetails.of(ErrorCode.INTERNAL_SERVER_ERROR, req, ex.getMessage());
        // 가장 많이 쓰이는 로깅 트레이스ID를 프로퍼티에 추가
        String traceId = MDC.get("traceId");
        if (traceId != null) pd.setProperty("traceId", traceId);
        return pd;
    }

    // 아이디 비밀번호 불일치 401
    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail handleBadCredentials(BadCredentialsException ex, HttpServletRequest req) {
        return ProblemDetails.of(
                HttpStatus.UNAUTHORIZED,
                "BAD_CREDENTIALS",
                "로그인 실패: 아이디 또는 비밀번호가 올바르지 않습니다.",
                "BAD_CREDENTIALS",
                Map.of(),
                req
        );
    }

    // 기타 인증 오류 401
    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException ex, HttpServletRequest req) {
        return ProblemDetails.of(
                HttpStatus.UNAUTHORIZED,
                "UNAUTHORIZED",
                "인증이 필요하거나 인증에 실패했습니다.",
                "UNAUTHORIZED",
                Map.of("reason", ex.getClass().getSimpleName()),
                req
        );
    }

    // 권한 부족 403
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        return ProblemDetails.of(
                HttpStatus.FORBIDDEN,
                "FORBIDDEN",
                "요청하신 리소스에 대한 권한이 없습니다.",
                "FORBIDDEN",
                Map.of(),
                req
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException ex, HttpServletRequest req) {
        String userMessage = "요청을 처리할 수 없습니다.";
        return ProblemDetails.of(
                HttpStatus.BAD_REQUEST,
                "DATA_INTEGRITY_VIOLATION",
                userMessage,
                "DATA_INTEGRITY_VIOLATION",
                Map.of(),
                req
        );
    }
}
