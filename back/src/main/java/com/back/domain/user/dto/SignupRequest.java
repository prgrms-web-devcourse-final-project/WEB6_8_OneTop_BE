package com.back.domain.user.dto;

import com.back.domain.user.entity.Gender;
import com.back.domain.user.entity.Mbti;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 사용자 회원가입 요청 시 필요한 정보를 담는 DTO 클래스.
 * 로그인 ID, 이메일, 비밀번호, 닉네임, 생년월일, 성별, MBTI, 가치관 등을 포함합니다.
 */
@Getter
@Setter
public class SignupRequest {

    @NotBlank(message = "로그인 아이디는 필수 입력 값입니다.")
    private String loginId;

    @NotBlank(message = "이메일은 필수 입력 값입니다.")
    @Email(message = "이메일 형식에 맞지 않습니다.")
    private String email;

    @NotBlank(message = "비밀번호는 필수 입력 값입니다.")
    @Pattern(regexp = "(?=.*[0-9])(?=.*[a-zA-Z])(?=.*\\W)(?=\\S+$).{8,20}",
            message = "비밀번호는 영문 대,소문자와 숫자, 특수기호가 적어도 1개 이상씩 포함된 8자 ~ 20자의 비밀번호여야 합니다.")
    private String password;

    @NotBlank(message = "닉네임은 필수 입력 값입니다.")
    private String nickname;

    @NotNull(message = "생년월일은 필수 입력 값입니다.")
    @Past(message = "생년월일은 과거 날짜여야 합니다.")
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime birthdayAt;

    @NotNull(message = "성별은 필수 입력 값입니다.")
    private Gender gender;

    @NotNull(message = "MBTI는 필수 입력 값입니다.")
    private Mbti mbti;

    @NotBlank(message = "가치관은 필수 입력 값입니다.")
    private String beliefs;
}
