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
    @DisplayName("회원가입 성공 → 201 Created + 응답에 email/role")
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
    @DisplayName("회원가입 - 중복 이메일 → 4xx")
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
    @DisplayName("로그인 성공 → 200 OK + 세션 생성 + 응답에 사용자 정보")
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
    @DisplayName("로그인 실패(비밀번호 오류) → 401 Unauthorized")
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
    @DisplayName("게스트 로그인 → 200 OK + 세션 발급 + 메시지")
    void t5() throws Exception {
        var result = mvc.perform(post(BASE + "/guest").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("게스트 로그인 성공"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
    }

    @Test
    @DisplayName("/me (익명) → 200 OK + message=anonymous")
    void t6() throws Exception {
        mvc.perform(get(BASE + "/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("anonymous"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("/me (인증됨) → 로그인 세션으로 조회")
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
    @DisplayName("로그아웃 → 200 OK + 메시지 확인")
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
