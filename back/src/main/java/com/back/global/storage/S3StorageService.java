package com.back.global.storage;

import com.back.global.ai.config.ImageAiConfig;
import com.back.global.exception.ApiException;
import com.back.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * AWS S3 스토리지 서비스 구현체 (프로덕션용)
 * storageType="s3"일 때만 활성화됩니다.
 *
 * CompletableFuture.supplyAsync() 사용하여 메모리 효율적 처리
 * S3Client는 기본 Connection Pool 사용 (리소스 최소화)
 * 파일명 저장
 * 파일명: UUID 기반으로 충돌 방지
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ai.image", name = "storage-type", havingValue = "s3")
public class S3StorageService implements StorageService {

    private final S3Client s3Client;
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

                String fileName = "scenario-" + UUID.randomUUID() + ".jpeg";

                PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(imageAiConfig.getS3BucketName())
                    .key(fileName)
                    .contentType("image/jpeg")
                    .build();

                // S3 업로드
                s3Client.putObject(putRequest, RequestBody.fromBytes(imageBytes));

                log.info("Image uploaded to S3 successfully with key: {}", fileName);
                return fileName;

            } catch (IllegalArgumentException e) {
                log.error("Invalid Base64 data: {}", e.getMessage());
                throw new ApiException(ErrorCode.STORAGE_INVALID_FILE, "Invalid Base64 image data: " + e.getMessage());
            } catch (S3Exception e) {
                log.error("S3 service error: {}", e.getMessage(), e);
                throw new ApiException(ErrorCode.S3_CONNECTION_FAILED, "S3 upload failed: " + e.awsErrorDetails().errorMessage());
            } catch (Exception e) {
                log.error("S3 upload failed: {}", e.getMessage(), e);
                throw new ApiException(ErrorCode.STORAGE_UPLOAD_FAILED, "Failed to upload image to S3: " + e.getMessage());
            }
        });
    }

    @Override
    public CompletableFuture<Void> deleteImage(String imageUrl) { // imageUrl is now the filename
        return CompletableFuture.runAsync(() -> {
            try {
                if (imageUrl == null || imageUrl.isEmpty()) {
                    throw new ApiException(ErrorCode.STORAGE_INVALID_FILE, "Image key cannot be null or empty for deletion.");
                }

                // S3 삭제 요청
                DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(imageAiConfig.getS3BucketName())
                    .key(imageUrl)
                    .build();

                s3Client.deleteObject(deleteRequest);
                log.info("Image deleted from S3: {}", String.valueOf(imageUrl));

            } catch (ApiException e) {
                throw e;
            } catch (Exception e) {
                if (e instanceof S3Exception) {
                    S3Exception s3e = (S3Exception) e;
                    log.error("S3 service error during deletion: {}", s3e.getMessage(), s3e);
                    throw new ApiException(ErrorCode.S3_CONNECTION_FAILED, "S3 delete failed: " + s3e.getMessage());
                } else {
                    log.error("Failed to delete image from S3: {}", e.getMessage(), e);
                    throw new ApiException(ErrorCode.STORAGE_DELETE_FAILED, "Failed to delete image from S3: " + e.getMessage());
                }
            }
        });
    }

    @Override
    public String getStorageType() {
        return "s3";
    }


}
