/*
 * [코드 흐름 요약]
 * - '도메인 용어 사전'을 pgvector로 관리하기 위한 엔티티.
 * - term(용어)와 embedding(vector)을 저장한다.
 * - 검색은 임베딩 유사도(<=>)로 수행한다.
 */
package com.back.domain.search.entity;

import com.back.infra.pgvector.PgVectorConverter;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "vocab_term")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class VocabTerm {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "term", nullable = false, unique = true, length = 128)
    private String term;

    @JdbcTypeCode(SqlTypes.OTHER)
    @Convert(converter = PgVectorConverter.class)
    @Column(name = "embedding", nullable = false, columnDefinition = "vector(768)")
    private float[] embedding;
}
