/**
 * [MAPPER-CORE] 매핑 오류 공통 예외
 * - 일관된 예외 타입으로 핸들/로깅 단순화
 */
package com.back.global.mapper;

public class MappingException extends RuntimeException {
    public MappingException(String message) { super(message); }
    public MappingException(String message, Throwable cause) { super(message, cause); }
}
