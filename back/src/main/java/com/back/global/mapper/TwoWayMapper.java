/**
 * [MAPPER-CORE] 양방향 매퍼 인터페이스
 * - RequestDTO ↔ Entity, Entity ↔ ResponseDTO 변환 규약
 */
package com.back.global.mapper;

public interface TwoWayMapper<REQ, ENTITY, RES> {

    // 중요: RequestDTO → Entity
    ENTITY toEntity(REQ request);

    // 중요: Entity → ResponseDTO
    RES toResponse(ENTITY entity);
}
