package com.back.domain.user.controller;

import com.back.domain.user.dto.LoginRequest;
import com.back.domain.user.entity.AuthProvider;
import com.back.domain.user.entity.Role;
import com.back.domain.user.entity.User;
import com.back.domain.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UserAuthController의 주요 인증/인가 기능을 검증하는 통합 테스트 클래스입니다.
 * SpringBootTest + MockMvc 환경에서 실제 요청/응답 흐름을 시뮬레이션합니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UserAuthControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private static final String BASE = "/api/v1/users-auth";

    private String toJson(Object o) throws Exception { return om.writeValueAsString(o); }

    private String uniqueEmail(String prefix) {
        return prefix + "+" + UUID.randomUUID().toString().substring(0,8) + "@example.com";
    }

    private User seedLocalUser(String email, String rawPassword) {
        User u = User.builder()
                .email(email)
                .password(passwordEncoder.encode(rawPassword))
                .username("tester")
                .nickname("tester_" + UUID.randomUUID().toString().substring(0,4))
                .birthdayAt(LocalDateTime.of(2000,1,1,0,0))
                .role(Role.USER)
                .authProvider(AuthProvider.LOCAL)
                .build();
        return userRepository.save(u);
    }

    @Test
    @DisplayName("성공 - 회원가입")
    void t1() throws Exception {
        var body = toJson(new SignupReq(
                uniqueEmail("join"),
                "Aa!23456",
                "홍길동",
                "길동이",
                LocalDateTime.of(1999,1,1,0,0)
        ));

        mvc.perform(post(BASE + "/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").exists())
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("실패 - 회원가입 중복 이메일")
    void t2() throws Exception {
        String email = uniqueEmail("dup");
        seedLocalUser(email, "Aa!23456");

        var body = toJson(new SignupReq(
                email,
                "Aa!23456",
                "김중복",
                "중복이",
                LocalDateTime.of(1995,5,5,0,0)
        ));

        mvc.perform(post(BASE + "/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("성공 - 로그인")
    void t3() throws Exception {
        String email = uniqueEmail("login-ok");
        seedLocalUser(email, "Aa!23456");

        var body = toJson(new LoginReq(email, "Aa!23456"));

        var result = mvc.perform(post(BASE + "/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email))
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
    }

    @Test
    @DisplayName("실패 - 로그인 비밀번호 오류")
    void t4() throws Exception {
        String email = uniqueEmail("login-fail");
        seedLocalUser(email, "Aa!23456");

        var body = toJson(new LoginReq(email, "Wrong!234"));

        mvc.perform(post(BASE + "/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("성공 - 게스트 로그인")
    void t5() throws Exception {
        var result = mvc.perform(post(BASE + "/guest").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("게스트 로그인 성공"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
    }

    @Test
    @DisplayName("성공 - /me (익명)")
    void t6() throws Exception {
        mvc.perform(get(BASE + "/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("anonymous"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("성공 - /me (인증됨)")
    void t7() throws Exception {
        String email = uniqueEmail("me");
        seedLocalUser(email, "Aa!23456");

        String loginJson = toJson(new LoginRequest(email, "Aa!23456"));

        MvcResult loginRes = mvc.perform(post(BASE + "/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginRes.getRequest().getSession(false);

        mvc.perform(get(BASE + "/me")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("authenticated"));
    }

    @Test
    @DisplayName("성공 - 로그아웃")
    void t8() throws Exception {
        String email = uniqueEmail("logout");
        seedLocalUser(email, "Aa!23456");
        var body = toJson(new LoginReq(email, "Aa!23456"));

        var loginRes = mvc.perform(post(BASE + "/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginRes.getRequest().getSession(false);
        assertThat(session).isNotNull();

        mvc.perform(post(BASE + "/logout")
                        .with(csrf())
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("로그아웃되었습니다"))
                .andExpect(jsonPath("$.message").value("로그아웃이 완료되었습니다"));
    }

    private record SignupReq(String email, String password, String username, String nickname, LocalDateTime birthdayAt) {}
    private record LoginReq(String email, String password) {}
}
