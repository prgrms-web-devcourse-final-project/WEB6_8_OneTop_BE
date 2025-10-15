/*
 * 이 파일은 연령대+카테고리 테마 사전 엔티티를 정의한다.
 * 흐름: 테마 문자열 + 임베딩 보관, min_age~max_age 및 category로 필터링
 */
package com.back.domain.search.entity;

import com.back.domain.node.entity.NodeCategory;
import com.back.infra.pgvector.PgVectorConverter;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "age_theme")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class AgeTheme {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false) private int minAge;
    @Column(nullable = false) private int maxAge;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NodeCategory category;

    @Column(nullable = false, columnDefinition = "text")
    private String theme;

    @JdbcTypeCode(SqlTypes.OTHER)
    @Convert(converter = PgVectorConverter.class)
    @Column(nullable = false, columnDefinition = "vector(768)")
    private float[] embedding;
}
