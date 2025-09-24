/**
 * [SERIALIZER] ProblemDetail → JSON (RFC7807 + 확장 필드 플래튼)
 * - 표준 필드(type/title/status/detail/instance) + properties(Map)을 JSON 루트로 평탄화
 */
package com.back.global.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.springframework.boot.jackson.JsonComponent;
import org.springframework.http.ProblemDetail;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

@JsonComponent
public class ProblemDetailJsonSerializer extends JsonSerializer<ProblemDetail> {

    // 가장 중요한: 표준 필드와 properties를 루트에 직렬화
    @Override
    public void serialize(ProblemDetail pd, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartObject();

        // type/title/detail/instance는 null 가능
        gen.writeStringField("type", toString(pd.getType()));
        if (pd.getTitle() != null) gen.writeStringField("title", pd.getTitle());
        gen.writeNumberField("status", pd.getStatus());
        if (pd.getDetail() != null) {
            gen.writeStringField("detail", pd.getDetail());
            gen.writeStringField("message", pd.getDetail());
        }
        if (pd.getInstance() != null) gen.writeStringField("instance", toString(pd.getInstance()));

        // (가장 많이 사용하는) 확장 속성 properties를 루트로 플래튼
        for (Map.Entry<String, Object> e : pd.getProperties().entrySet()) {
            Object v = e.getValue();
            if (v != null) gen.writeObjectField(e.getKey(), v);
        }
        gen.writeEndObject();
    }

    // 한 줄 요약: URI를 문자열로 변환
    private static String toString(URI uri) { return uri.toString(); }
}
