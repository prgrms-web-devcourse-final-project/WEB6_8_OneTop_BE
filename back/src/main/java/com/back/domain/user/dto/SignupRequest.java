package com.back.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;

import java.time.LocalDateTime;

/**
 * 사용자 회원가입 요청 시 필요한 정보를 담는 DTO 클래스.
 * 로그인 ID, 이메일, 비밀번호, 사용자 이름, 닉네임, 생년월일을 포함합니다.
 */
public record SignupRequest(

        @NotBlank(message = "이메일은 필수 입력 값입니다.")
        @Email(message = "이메일 형식에 맞지 않습니다.")
        String email,

        @NotBlank(message = "비밀번호는 필수 입력 값입니다.")
        @Pattern(regexp="(?=.*[0-9])(?=.*[a-zA-Z])(?=.*\\W)(?=\\S+$).{8,20}",
                message="비밀번호는 영문, 숫자, 특수기호 포함 8~20자여야 합니다.")
        String password,

        @NotBlank(message = "이름은 필수 입력 값입니다.")
        String username,

        @NotBlank(message = "닉네임은 필수 입력 값입니다.")
        String nickname,

        @NotNull(message = "생년월일은 필수 입력 값입니다.")
        @Past(message = "생년월일은 과거 날짜여야 합니다.")
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") // JSON 바인딩용(폼 바인딩이면 @DateTimeFormat)
        LocalDateTime birthdayAt
) {}
