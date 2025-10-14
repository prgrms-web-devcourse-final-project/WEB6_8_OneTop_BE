/*
 * 이 파일은 RAG 검색용 스니펫 엔티티를 정의한다.
 * 라인/나이/카테고리/텍스트/임베딩을 저장하며 pgvector 컬럼을 float[]로 매핑한다.
 */
package com.back.domain.search.entity;

import com.back.infra.pgvector.PgVectorConverter;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "node_snippet")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class NodeSnippet {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "line_id", nullable = false)
    private Long lineId;

    @Column(name = "age_year", nullable = false)
    private Integer ageYear;

    private String category;

    @Column(name = "text", nullable = false, columnDefinition = "text")
    private String text;

    @JdbcTypeCode(SqlTypes.OTHER)
    @Convert(converter = PgVectorConverter.class)
    @Column(name = "embedding", nullable = false, columnDefinition = "vector(768)")
    private float[] embedding;


}
