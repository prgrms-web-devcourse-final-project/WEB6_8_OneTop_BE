package com.back.global.storage;

import java.util.concurrent.CompletableFuture;

/**
 * 스토리지 서비스 인터페이스
 * 이미지 업로드/삭제 기능을 추상화합니다.
 *
 * 구현체:
 * - S3StorageService: AWS S3에 업로드 (프로덕션)
 * - LocalStorageService: 로컬 파일 시스템에 저장 (개발)
 *
 * CompletableFuture를 사용하여 AiService와 동일한 비동기 패턴 유지
 */
public interface StorageService {

    // @return 업로드된 이미지 URL (S3 URL 또는 로컬 URL)
    CompletableFuture<String> uploadBase64Image(String base64Data);

    // @return 삭제 완료 Future
    CompletableFuture<Void> deleteImage(String imageUrl);

    // @return "s3" 또는 "local"
    String getStorageType();
}
