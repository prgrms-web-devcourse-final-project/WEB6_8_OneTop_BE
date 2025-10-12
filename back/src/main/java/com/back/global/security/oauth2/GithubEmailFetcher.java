package com.back.global.security.oauth2;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class GithubEmailFetcher {

    private final WebClient github;

    public GithubEmailFetcher(@Qualifier("githubWebClient") WebClient github) {
        this.github = github;
    }

    public String fetchPrimaryEmail(String accessToken) {
        try {
            List<Map<String, Object>> emails = github.get()
                    .uri("/user/emails")
                    .header(HttpHeaders.AUTHORIZATION, "token " + accessToken)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                    .block();

            if (emails == null || emails.isEmpty()) return null;

            for (var e : emails)
                if (Boolean.TRUE.equals(e.get("primary")) && Boolean.TRUE.equals(e.get("verified")))
                    return (String) e.get("email");

            for (var e : emails)
                if (Boolean.TRUE.equals(e.get("verified")))
                    return (String) e.get("email");

            return (String) emails.get(0).get("email");
        } catch (Exception e) {
            log.warn("GitHub 이메일 추가 조회 실패: {}", e.getMessage());
            return null;
        }
    }
}
