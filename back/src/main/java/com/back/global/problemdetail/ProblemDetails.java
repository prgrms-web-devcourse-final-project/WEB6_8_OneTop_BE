/**
 * ProblemDetails
 * - ErrorCode/HttpStatus 기반으로 RFC7807 ProblemDetail을 손쉽게 생성하는 유틸
 * - instance/path/timestamp/code/traceId 등의 공통 속성 자동 세팅
 * - 오버로드로 detail/추가 속성(Map) 주입 가능
 */
package com.back.global.problemdetail;

import com.back.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Map;

public final class ProblemDetails {

    private ProblemDetails() {}

    // 가장 많이 쓰는 생성기(핵심): ErrorCode + (선택)detail
    public static ProblemDetail of(ErrorCode code, HttpServletRequest req, Object... args) {
        // 메시지 서식 적용
        String detail = (args == null || args.length == 0)
                ? code.getMessage()
                : String.format(code.getMessage(), args);

        // ProblemDetail 생성 + 공통 속성 세팅
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(code.getStatus(), detail);
        pd.setTitle(code.name());
        pd.setType(URI.create("about:blank"));
        setCommonProps(pd, req, code.getCode());
        return pd;
    }

    // HttpStatus/제목/상세/코드/추가속성 버전
    public static ProblemDetail of(HttpStatus status,
                                   String title,
                                   String detail,
                                   String code,
                                   Map<String, Object> properties,
                                   HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        pd.setType(URI.create("about:blank"));
        setCommonProps(pd, req, code);
        if (properties != null) properties.forEach(pd::setProperty);
        return pd;
    }

    // 공통 속성 세팅(가장 많이 호출됨)
    private static void setCommonProps(ProblemDetail pd, HttpServletRequest req, String code) {
        // 가장 중요한 공통 속성 한 줄 요약: path/instance/timestamp/code/traceId를 세팅
        pd.setProperty("timestamp", OffsetDateTime.now().toString());
        if (code != null) pd.setProperty("code", code);
        if (req != null) {
            pd.setInstance(URI.create(req.getRequestURI()));
            pd.setProperty("path", req.getRequestURI());
        }
        String traceId = MDC.get("traceId");
        if (traceId != null) pd.setProperty("traceId", traceId);
    }
}
