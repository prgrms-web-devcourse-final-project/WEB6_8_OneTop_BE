/*
 * [파일 요약/코드 흐름]
 * - JPA <-> PostgreSQL(pgvector) 매핑 컨버터
 * - DB 저장/조회 시 차원 검증(768)과 NaN/Inf 정리로 무결성 강화
 */
package com.back.infra.pgvector;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.postgresql.util.PGobject;

@Converter(autoApply = false)
public class PgVectorConverter implements AttributeConverter<float[], Object> {

    // 무결성 검증
    private static final int DIM = 768;

    // next 노드 생성
    @Override
    public Object convertToDatabaseColumn(float[] attribute) {
        if (attribute == null || attribute.length == 0) return null;
        if (attribute.length != DIM) {
            throw new IllegalArgumentException("vector dim must be " + DIM + " but got " + attribute.length);
        }
        try {
            PGobject obj = new PGobject();
            obj.setType("vector");
            obj.setValue(toLiteral(attribute));
            return obj;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to convert float[] to PGobject(vector)", e);
        }
    }

    // 무결성 검증
    @Override
    public float[] convertToEntityAttribute(Object dbData) {
        if (dbData == null) return new float[0];
        String s = (dbData instanceof PGobject pgo) ? pgo.getValue() : dbData.toString();
        if (s == null) return new float[0];
        s = s.trim();
        if (s.isEmpty() || "[]".equals(s)) return new float[0];
        if (s.startsWith("[") && s.endsWith("]")) s = s.substring(1, s.length() - 1);
        if (s.isBlank()) return new float[0];

        String[] parts = s.split("\\s*,\\s*");
        float[] out = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            float x = Float.parseFloat(parts[i]);
            // 무결성 검증
            out[i] = (Float.isNaN(x) || Float.isInfinite(x)) ? 0f : x;
        }
        return out;
    }

    // 무결성 검증
    private String toLiteral(float[] v) {
        StringBuilder sb = new StringBuilder(v.length * 8 + 2);
        sb.append('[');
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            float x = v[i];
            if (Float.isNaN(x) || Float.isInfinite(x)) x = 0f;
            sb.append(Float.toString(x));
        }
        sb.append(']');
        return sb.toString();
    }
}
