package com.back.global.storage;

import com.back.global.ai.config.ImageAiConfig;
import com.back.global.exception.ApiException;
import com.back.global.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

/**
 * LocalStorageService 단위 테스트.
 * 로컬 파일 시스템 기반 이미지 저장 로직을 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LocalStorageService 단위 테스트")
class LocalStorageServiceTest {

    @Mock
    private ImageAiConfig imageAiConfig;

    @InjectMocks
    private LocalStorageService localStorageService;

    private static final String TEST_STORAGE_PATH = "./test-uploads/images";
    private static final String TEST_BASE_URL = "http://localhost:8080/test-images";
    private static final String VALID_BASE64 = Base64.getEncoder().encodeToString("test image data".getBytes());

    @BeforeEach
    void setUp() {
        // 테스트용 설정 모킹 (lenient로 설정하여 불필요한 stubbing 경고 방지)
        lenient().when(imageAiConfig.getLocalStoragePath()).thenReturn(TEST_STORAGE_PATH);
        lenient().when(imageAiConfig.getLocalBaseUrl()).thenReturn(TEST_BASE_URL);
    }

    @AfterEach
    void tearDown() throws IOException {
        // 테스트 파일 및 디렉토리 정리
        Path testDir = Paths.get(TEST_STORAGE_PATH);
        if (Files.exists(testDir)) {
            Files.walk(testDir)
                    .sorted((a, b) -> -a.compareTo(b)) // 파일 먼저 삭제, 디렉토리 나중에 삭제
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            // 테스트 정리 중 에러는 무시
                        }
                    });
        }
    }

    @Nested
    @DisplayName("로컬 업로드")
    class UploadBase64ImageTests {

        @Test
        @DisplayName("성공 - Base64 이미지 업로드")
        void uploadBase64Image_성공_Base64_이미지_업로드() throws ExecutionException, InterruptedException, IOException {
            // Given
            String base64Data = VALID_BASE64;

            // When
            CompletableFuture<String> resultFuture = localStorageService.uploadBase64Image(base64Data);
            String resultUrl = resultFuture.get();

            // Then
            assertThat(resultUrl).isNotNull();
            assertThat(resultUrl).startsWith(TEST_BASE_URL);
            assertThat(resultUrl).contains("scenario-");
            assertThat(resultUrl).endsWith(".jpeg");

            // 실제 파일이 생성되었는지 확인
            String fileName = resultUrl.substring(resultUrl.lastIndexOf('/') + 1);
            Path savedFile = Paths.get(TEST_STORAGE_PATH).resolve(fileName);
            assertThat(Files.exists(savedFile)).isTrue();
            assertThat(Files.size(savedFile)).isGreaterThan(0);
        }

        @Test
        @DisplayName("성공 - 디렉토리 자동 생성")
        void uploadBase64Image_성공_디렉토리_자동생성() throws ExecutionException, InterruptedException, IOException {
            // Given
            String base64Data = VALID_BASE64;
            Path testDir = Paths.get(TEST_STORAGE_PATH);

            // 디렉토리가 없는 상태 확인
            if (Files.exists(testDir)) {
                Files.walk(testDir)
                        .sorted((a, b) -> -a.compareTo(b))
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException e) {
                                // ignore
                            }
                        });
            }
            assertThat(Files.exists(testDir)).isFalse();

            // When
            CompletableFuture<String> resultFuture = localStorageService.uploadBase64Image(base64Data);
            String resultUrl = resultFuture.get();

            // Then
            assertThat(resultUrl).isNotNull();
            assertThat(Files.exists(testDir)).isTrue(); // 디렉토리 자동 생성 확인
        }

        @Test
        @DisplayName("실패 - 잘못된 Base64 데이터")
        void uploadBase64Image_실패_잘못된_Base64_데이터() {
            // Given
            String invalidBase64 = "this-is-not-base64!!!";

            // When
            CompletableFuture<String> resultFuture = localStorageService.uploadBase64Image(invalidBase64);

            // Then
            assertThatThrownBy(resultFuture::get)
                    .hasCauseInstanceOf(ApiException.class)
                    .cause()
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.STORAGE_INVALID_FILE);
        }

        @Test
        @DisplayName("성공 - UUID 파일명 중복 없음")
        void uploadBase64Image_성공_UUID_파일명_중복없음() throws ExecutionException, InterruptedException {
            // Given
            String base64Data = VALID_BASE64;

            // When - 3번 업로드
            String url1 = localStorageService.uploadBase64Image(base64Data).get();
            String url2 = localStorageService.uploadBase64Image(base64Data).get();
            String url3 = localStorageService.uploadBase64Image(base64Data).get();

            // Then - 모두 다른 URL이어야 함 (UUID 덕분)
            assertThat(url1).isNotEqualTo(url2);
            assertThat(url2).isNotEqualTo(url3);
            assertThat(url1).isNotEqualTo(url3);
        }
    }

    @Nested
    @DisplayName("로컬 삭제")
    class DeleteImageTests {

        @Test
        @DisplayName("성공 - 이미지 삭제")
        void deleteImage_성공_이미지_삭제() throws ExecutionException, InterruptedException {
            // Given - 먼저 이미지 업로드
            String base64Data = VALID_BASE64;
            String uploadedUrl = localStorageService.uploadBase64Image(base64Data).get();

            // 파일 존재 확인
            String fileName = uploadedUrl.substring(uploadedUrl.lastIndexOf('/') + 1);
            Path filePath = Paths.get(TEST_STORAGE_PATH).resolve(fileName);
            assertThat(Files.exists(filePath)).isTrue();

            // When - 삭제
            CompletableFuture<Void> deleteFuture = localStorageService.deleteImage(uploadedUrl);
            deleteFuture.get();

            // Then - 파일이 삭제되었는지 확인
            assertThat(Files.exists(filePath)).isFalse();
        }

        @Test
        @DisplayName("성공 - 파일 없음 (경고 로그만)")
        void deleteImage_성공_파일없음_경고로그만() {
            // Given - 존재하지 않는 URL
            String nonExistentUrl = TEST_BASE_URL + "/non-existent-file.jpeg";

            // When & Then - 예외 발생 안 함 (경고 로그만)
            assertThatCode(() -> localStorageService.deleteImage(nonExistentUrl).get())
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("실패 - null URL")
        void deleteImage_실패_null_URL() {
            // Given
            String nullUrl = null;

            // When
            CompletableFuture<Void> deleteFuture = localStorageService.deleteImage(nullUrl);

            // Then
            // extractFileNameFromUrl에서 STORAGE_INVALID_FILE을 던지지만
            // deleteImage의 catch (Exception e)에서 STORAGE_DELETE_FAILED로 래핑됨
            assertThatThrownBy(deleteFuture::get)
                    .hasCauseInstanceOf(ApiException.class)
                    .cause()
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.STORAGE_DELETE_FAILED);
        }

        @Test
        @DisplayName("실패 - 빈 URL")
        void deleteImage_실패_빈_URL() {
            // Given
            String emptyUrl = "";

            // When
            CompletableFuture<Void> deleteFuture = localStorageService.deleteImage(emptyUrl);

            // Then
            // extractFileNameFromUrl에서 STORAGE_INVALID_FILE을 던지지만
            // deleteImage의 catch (Exception e)에서 STORAGE_DELETE_FAILED로 래핑됨
            assertThatThrownBy(deleteFuture::get)
                    .hasCauseInstanceOf(ApiException.class)
                    .cause()
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.STORAGE_DELETE_FAILED);
        }
    }

    @Nested
    @DisplayName("URL 파싱")
    class ExtractFileNameTests {

        @Test
        @DisplayName("성공 - URL에서 파일명 추출")
        void extractFileName_성공_URL에서_파일명_추출() throws ExecutionException, InterruptedException {
            // Given
            String base64Data = VALID_BASE64;
            String uploadedUrl = localStorageService.uploadBase64Image(base64Data).get();

            // When - deleteImage 내부에서 extractFileNameFromUrl 호출됨
            String expectedFileName = uploadedUrl.substring(uploadedUrl.lastIndexOf('/') + 1);

            // Then
            assertThat(expectedFileName).startsWith("scenario-");
            assertThat(expectedFileName).endsWith(".jpeg");
            assertThat(expectedFileName).contains("-"); // UUID 구분자
        }

        @Test
        @DisplayName("성공 - 복잡한 URL 파싱")
        void extractFileName_성공_복잡한_URL_파싱() {
            // Given - 복잡한 URL
            String complexUrl = "http://localhost:8080/api/v1/images/scenario-123e4567-e89b-12d3-a456-426614174000.jpeg";

            // When & Then - deleteImage가 정상적으로 파일명 추출하는지 확인 (예외 안 남)
            assertThatCode(() -> localStorageService.deleteImage(complexUrl).get())
                    .doesNotThrowAnyException(); // 파일 없어도 경고만 출력
        }
    }

    @Nested
    @DisplayName("스토리지 타입")
    class GetStorageTypeTests {

        @Test
        @DisplayName("성공 - 스토리지 타입 확인")
        void getStorageType_성공_스토리지_타입_확인() {
            // When
            String storageType = localStorageService.getStorageType();

            // Then
            assertThat(storageType).isEqualTo("local");
        }
    }
}
