package com.back.global.storage;

import com.back.global.ai.config.ImageAiConfig;
import com.back.global.exception.ApiException;
import com.back.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 로컬 파일 시스템 스토리지 서비스 구현체 (개발용)
 * storageType="local"일 때만 활성화됩니다.
 *
 * 로컬 개발 환경 최적화:
 * 파일 시스템에 직접 저장 (S3 설정 불필요)
 * 업로드된 이미지는 ./uploads/images/ 디렉토리에 저장
 * URL은 http://localhost:8080/images/{fileName} 형태로 반환
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ai.image", name = "storage-type", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {

    private final ImageAiConfig imageAiConfig;

    @Override
    public CompletableFuture<String> uploadBase64Image(String base64Data) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Input validation
                if (base64Data == null || base64Data.isEmpty()) {
                    throw new ApiException(ErrorCode.STORAGE_INVALID_FILE, "Base64 data cannot be null or empty");
                }

                // Base64 디코딩
                byte[] imageBytes = Base64.getDecoder().decode(base64Data);
                log.debug("Decoded Base64 image, size: {} bytes", imageBytes.length);

                Path uploadDir = Paths.get(imageAiConfig.getLocalStoragePath());
                if (!Files.exists(uploadDir)) {
                    Files.createDirectories(uploadDir);
                    log.info("Created upload directory: {}", uploadDir.toAbsolutePath());
                }

                String fileName = "scenario-" + UUID.randomUUID() + ".jpeg";
                Path filePath = uploadDir.resolve(fileName);

                Files.write(filePath, imageBytes);

                String localUrl = imageAiConfig.getLocalBaseUrl() + "/" + fileName;

                log.info("Image saved locally: {}", filePath.toAbsolutePath());
                log.info("Local URL: {}", localUrl);

                return localUrl;

            } catch (IllegalArgumentException e) {
                log.error("Invalid Base64 data: {}", e.getMessage());
                throw new ApiException(ErrorCode.STORAGE_INVALID_FILE, "Invalid Base64 image data: " + e.getMessage());
            } catch (IOException e) {
                log.error("Failed to save image locally: {}", e.getMessage(), e);
                throw new ApiException(ErrorCode.LOCAL_STORAGE_IO_ERROR, "Failed to save image to local storage: " + e.getMessage());
            } catch (Exception e) {
                log.error("Unexpected error during local image upload: {}", e.getMessage(), e);
                throw new ApiException(ErrorCode.STORAGE_UPLOAD_FAILED, "Failed to upload image locally: " + e.getMessage());
            }
        });
    }

    @Override
    public CompletableFuture<Void> deleteImage(String imageUrl) {
        return CompletableFuture.runAsync(() -> {
            try {
                // URL에서 파일명 추출
                String fileName = extractFileNameFromUrl(imageUrl);

                // 파일 경로 생성
                Path filePath = Paths.get(imageAiConfig.getLocalStoragePath()).resolve(fileName);

                // 파일 삭제
                if (Files.exists(filePath)) {
                    Files.delete(filePath);
                    log.info("Image deleted locally: {}", filePath.toAbsolutePath());
                } else {
                    log.warn("Image file not found for deletion: {}", filePath.toAbsolutePath());
                }

            } catch (IOException e) {
                log.error("Failed to delete local image: {}", e.getMessage(), e);
                throw new ApiException(ErrorCode.LOCAL_STORAGE_IO_ERROR, "Failed to delete image from local storage: " + e.getMessage());
            } catch (Exception e) {
                log.error("Unexpected error during local image deletion: {}", e.getMessage(), e);
                throw new ApiException(ErrorCode.STORAGE_DELETE_FAILED, "Failed to delete image locally: " + e.getMessage());
            }
        });
    }

    @Override
    public String getStorageType() {
        return "local";
    }

    /**
     * URL에서 파일명 추출
     * 예: http://localhost:8080/images/scenario-uuid.jpeg → scenario-uuid.jpeg
     */
    private String extractFileNameFromUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            throw new ApiException(ErrorCode.STORAGE_INVALID_FILE, "Image URL cannot be null or empty");
        }

        String[] parts = imageUrl.split("/");
        return parts[parts.length - 1];
    }
}
