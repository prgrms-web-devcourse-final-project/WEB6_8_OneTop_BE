/*
 * 이 파일은 텍스트를 임베딩 벡터(float[])로 변환하는 클라이언트 인터페이스를 정의한다.
 * 구현체는 OpenAI/Vertex/사내 API 등으로 자유롭게 교체 가능하다.
 */
package com.back.global.ai.vector;

public interface EmbeddingClient {

    // 입력 텍스트를 임베딩 벡터로 변환한다.
    float[] embed(String text);
}
