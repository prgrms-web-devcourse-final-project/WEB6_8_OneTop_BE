/*
 * [파일 요약/코드 흐름]
 * - JPA <-> PostgreSQL(pgvector) 매핑 컨버터
 * - DB 저장 시 float[]를 PGobject(type="vector")로 바인딩하여 드라이버가 네이티브 vector 타입으로 전달
 * - DB 조회 시 PGobject 또는 문자열("[a,b,c]")을 안전하게 파싱해 float[]로 복원
 * - 문자열로 바인딩할 때 발생하던 "column is of type vector but expression is of type character varying" 오류를 제거
 */
package com.back.infra.pgvector;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.postgresql.util.PGobject;

@Converter(autoApply = false)
public class PgVectorConverter implements AttributeConverter<float[], Object> {

    // 가장 중요한 함수: float[] -> PGobject(vector)로 직렬화해 네이티브 타입 바인딩
    @Override
    public Object convertToDatabaseColumn(float[] attribute) {
        if (attribute == null || attribute.length == 0) return null; // NOT NULL 컬럼이면 상위에서 보장
        try {
            PGobject obj = new PGobject();
            obj.setType("vector");                 // pgvector 타입 지정
            obj.setValue(toLiteral(attribute));    // "[a,b,c]" 형식 값 설정
            return obj;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to convert float[] to PGobject(vector)", e);
        }
    }

    // 가장 많이 사용하는 함수: PGobject/문자열 -> float[]로 역직렬화
    @Override
    public float[] convertToEntityAttribute(Object dbData) {
        if (dbData == null) return new float[0];
        String s;
        if (dbData instanceof PGobject pgo) {
            s = pgo.getValue();
        } else {
            s = dbData.toString();
        }
        if (s == null) return new float[0];
        s = s.trim();
        if (s.isEmpty() || "[]".equals(s)) return new float[0];

        if (s.startsWith("[") && s.endsWith("]")) {
            s = s.substring(1, s.length() - 1);
        }
        if (s.isBlank()) return new float[0];

        String[] parts = s.split("\\s*,\\s*");
        float[] out = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            out[i] = Float.parseFloat(parts[i]);
        }
        return out;
    }

    // 가장 중요한 함수: float[]를 pgvector 리터럴("[...]") 문자열로 변환
    private String toLiteral(float[] v) {
        StringBuilder sb = new StringBuilder(v.length * 8 + 2);
        sb.append('[');
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(Float.toString(v[i]));
        }
        sb.append(']');
        return sb.toString();
    }
}
