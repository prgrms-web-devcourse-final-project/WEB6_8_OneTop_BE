/**
 * [MAPPER-CORE] 단방향 매퍼 인터페이스
 * - DTO→Entity 또는 Entity→DTO 등 한 방향 변환에 사용
 */
package com.back.global.mapper;

@FunctionalInterface
public interface Mapper<S, T> {

    // 중요: 단방향 변환
    T map(S source);
}
