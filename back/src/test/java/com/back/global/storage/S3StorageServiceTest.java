package com.back.global.storage;

import com.back.global.ai.config.ImageAiConfig;
import com.back.global.exception.ApiException;
import com.back.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.*;

/**
 * S3StorageService 단위 테스트.
 * AWS S3 기반 이미지 저장 로직을 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("S3StorageService 단위 테스트")
class S3StorageServiceTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private ImageAiConfig imageAiConfig;

    @InjectMocks
    private S3StorageService s3StorageService;

    private static final String TEST_BUCKET_NAME = "test-bucket";
    private static final String TEST_REGION = "ap-northeast-2";
    private static final String VALID_BASE64 = Base64.getEncoder().encodeToString("test image data".getBytes());

    @BeforeEach
    void setUp() {
        // 테스트용 설정 모킹 (lenient로 설정)
        lenient().when(imageAiConfig.getS3BucketName()).thenReturn(TEST_BUCKET_NAME);
        lenient().when(imageAiConfig.getS3Region()).thenReturn(TEST_REGION);
    }

    @Nested
    @DisplayName("S3 업로드")
    class UploadBase64ImageTests {

        @Test
        @DisplayName("성공 - Base64 이미지 S3 업로드")
        void uploadBase64Image_성공_Base64_이미지_S3_업로드() throws ExecutionException, InterruptedException {
            // Given
            String base64Data = VALID_BASE64;

            // S3Client.putObject() 모킹 (PutObjectResponse 반환)
            PutObjectResponse mockResponse = PutObjectResponse.builder().build();
            given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                    .willReturn(mockResponse);

            // When
            CompletableFuture<String> resultFuture = s3StorageService.uploadBase64Image(base64Data);
            String resultUrl = resultFuture.get();

            // Then
            assertThat(resultUrl).isNotNull();
            assertThat(resultUrl).startsWith("https://" + TEST_BUCKET_NAME + ".s3." + TEST_REGION + ".amazonaws.com/");
            assertThat(resultUrl).contains("scenario-");
            assertThat(resultUrl).endsWith(".jpeg");

            // S3Client 호출 검증
            verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        }

        @Test
        @DisplayName("실패 - 잘못된 Base64 데이터")
        void uploadBase64Image_실패_잘못된_Base64_데이터() {
            // Given
            String invalidBase64 = "this-is-not-base64!!!";

            // When
            CompletableFuture<String> resultFuture = s3StorageService.uploadBase64Image(invalidBase64);

            // Then
            assertThatThrownBy(resultFuture::get)
                    .hasCauseInstanceOf(ApiException.class)
                    .cause()
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.STORAGE_INVALID_FILE);

            // S3Client는 호출되지 않아야 함
            verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        }

        @Test
        @DisplayName("실패 - S3 서비스 에러")
        void uploadBase64Image_실패_S3_서비스_에러() {
            // Given
            String base64Data = VALID_BASE64;

            // S3Exception 모킹
            AwsErrorDetails errorDetails = AwsErrorDetails.builder()
                    .errorMessage("Access Denied")
                    .build();
            S3Exception s3Exception = (S3Exception) S3Exception.builder()
                    .awsErrorDetails(errorDetails)
                    .message("S3 Error")
                    .build();

            doThrow(s3Exception).when(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));

            // When
            CompletableFuture<String> resultFuture = s3StorageService.uploadBase64Image(base64Data);

            // Then
            assertThatThrownBy(resultFuture::get)
                    .hasCauseInstanceOf(ApiException.class)
                    .cause()
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.S3_CONNECTION_FAILED);

            verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        }

        @Test
        @DisplayName("성공 - UUID 파일명 중복 없음")
        void uploadBase64Image_성공_UUID_파일명_중복없음() throws ExecutionException, InterruptedException {
            // Given
            String base64Data = VALID_BASE64;
            PutObjectResponse mockResponse = PutObjectResponse.builder().build();
            given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                    .willReturn(mockResponse);

            // When - 3번 업로드
            String url1 = s3StorageService.uploadBase64Image(base64Data).get();
            String url2 = s3StorageService.uploadBase64Image(base64Data).get();
            String url3 = s3StorageService.uploadBase64Image(base64Data).get();

            // Then - 모두 다른 URL이어야 함 (UUID 덕분)
            assertThat(url1).isNotEqualTo(url2);
            assertThat(url2).isNotEqualTo(url3);
            assertThat(url1).isNotEqualTo(url3);

            // S3Client 3번 호출 확인
            verify(s3Client, times(3)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        }

        @Test
        @DisplayName("성공 - S3 URL 형식 확인")
        void uploadBase64Image_성공_S3_URL_형식_확인() throws ExecutionException, InterruptedException {
            // Given
            String base64Data = VALID_BASE64;
            PutObjectResponse mockResponse = PutObjectResponse.builder().build();
            given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                    .willReturn(mockResponse);

            // When
            String resultUrl = s3StorageService.uploadBase64Image(base64Data).get();

            // Then - S3 표준 URL 형식: https://{bucket}.s3.{region}.amazonaws.com/{key}
            assertThat(resultUrl).matches("https://test-bucket\\.s3\\.ap-northeast-2\\.amazonaws\\.com/scenario-[a-f0-9\\-]+\\.jpeg");
        }
    }

    @Nested
    @DisplayName("S3 삭제")
    class DeleteImageTests {

        @Test
        @DisplayName("성공 - S3 이미지 삭제")
        void deleteImage_성공_S3_이미지_삭제() throws ExecutionException, InterruptedException {
            // Given
            String s3Url = "https://test-bucket.s3.ap-northeast-2.amazonaws.com/scenario-test-uuid.jpeg";
            DeleteObjectResponse mockResponse = DeleteObjectResponse.builder().build();
            given(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                    .willReturn(mockResponse);

            // When
            CompletableFuture<Void> deleteFuture = s3StorageService.deleteImage(s3Url);
            deleteFuture.get();

            // Then
            verify(s3Client, times(1)).deleteObject(any(DeleteObjectRequest.class));
        }

        @Test
        @DisplayName("실패 - S3 서비스 에러")
        void deleteImage_실패_S3_서비스_에러() {
            // Given
            String s3Url = "https://test-bucket.s3.ap-northeast-2.amazonaws.com/scenario-test-uuid.jpeg";

            AwsErrorDetails errorDetails = AwsErrorDetails.builder()
                    .errorMessage("NoSuchKey")
                    .build();
            S3Exception s3Exception = (S3Exception) S3Exception.builder()
                    .awsErrorDetails(errorDetails)
                    .message("S3 Error")
                    .build();

            doThrow(s3Exception).when(s3Client).deleteObject(any(DeleteObjectRequest.class));

            // When
            CompletableFuture<Void> deleteFuture = s3StorageService.deleteImage(s3Url);

            // Then
            assertThatThrownBy(deleteFuture::get)
                    .hasCauseInstanceOf(ApiException.class)
                    .cause()
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.S3_CONNECTION_FAILED);

            verify(s3Client, times(1)).deleteObject(any(DeleteObjectRequest.class));
        }

        @Test
        @DisplayName("실패 - null URL")
        void deleteImage_실패_null_URL() {
            // Given
            String nullUrl = null;

            // When
            CompletableFuture<Void> deleteFuture = s3StorageService.deleteImage(nullUrl);

            // Then
            assertThatThrownBy(deleteFuture::get)
                    .hasCauseInstanceOf(ApiException.class)
                    .cause()
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.STORAGE_DELETE_FAILED);

            // S3Client는 호출되지 않아야 함
            verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
        }

        @Test
        @DisplayName("실패 - 빈 URL")
        void deleteImage_실패_빈_URL() {
            // Given
            String emptyUrl = "";

            // When
            CompletableFuture<Void> deleteFuture = s3StorageService.deleteImage(emptyUrl);

            // Then
            assertThatThrownBy(deleteFuture::get)
                    .hasCauseInstanceOf(ApiException.class)
                    .cause()
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.STORAGE_DELETE_FAILED);

            // S3Client는 호출되지 않아야 함
            verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
        }
    }

    @Nested
    @DisplayName("URL 파싱")
    class ExtractFileNameTests {

        @Test
        @DisplayName("성공 - S3 URL에서 파일명 추출")
        void extractFileName_성공_S3_URL에서_파일명_추출() throws ExecutionException, InterruptedException {
            // Given
            String s3Url = "https://test-bucket.s3.ap-northeast-2.amazonaws.com/scenario-abc-123.jpeg";
            DeleteObjectResponse mockResponse = DeleteObjectResponse.builder().build();
            given(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                    .willReturn(mockResponse);

            // When - deleteImage 내부에서 extractFileNameFromUrl 호출됨
            s3StorageService.deleteImage(s3Url).get();

            // Then - 정상적으로 파일명 추출 및 삭제 요청 성공
            verify(s3Client, times(1)).deleteObject(argThat((DeleteObjectRequest request) ->
                    request.key().equals("scenario-abc-123.jpeg")
            ));
        }

        @Test
        @DisplayName("성공 - 복잡한 S3 URL 파싱")
        void extractFileName_성공_복잡한_S3_URL_파싱() throws ExecutionException, InterruptedException {
            // Given - 경로가 있는 복잡한 URL
            String complexUrl = "https://test-bucket.s3.ap-northeast-2.amazonaws.com/images/2024/scenario-test.jpeg";
            DeleteObjectResponse mockResponse = DeleteObjectResponse.builder().build();
            given(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                    .willReturn(mockResponse);

            // When
            s3StorageService.deleteImage(complexUrl).get();

            // Then - 마지막 부분만 추출
            verify(s3Client, times(1)).deleteObject(argThat((DeleteObjectRequest request) ->
                    request.key().equals("scenario-test.jpeg")
            ));
        }
    }

    @Nested
    @DisplayName("스토리지 타입")
    class GetStorageTypeTests {

        @Test
        @DisplayName("성공 - 스토리지 타입 확인")
        void getStorageType_성공_스토리지_타입_확인() {
            // When
            String storageType = s3StorageService.getStorageType();

            // Then
            assertThat(storageType).isEqualTo("s3");
        }
    }
}
