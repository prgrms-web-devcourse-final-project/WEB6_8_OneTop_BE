package com.back.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * 애플리케이션의 CORS(Cross-Origin Resource Sharing) 정책을 설정하는 구성 클래스입니다.
 */
@Configuration
public class CorsConfig {
    @Value("${custom.site.frontUrl}")
    private String frontUrl;

    @Bean
    public CorsConfigurationSource corsConfigurationSource(){
        CorsConfiguration conf = new CorsConfiguration();
        conf.setAllowedOriginPatterns(List.of("http://localhost:*", frontUrl));
        conf.setAllowedMethods(List.of("GET","POST","PUT","DELETE","PATCH","OPTIONS"));
        conf.setAllowedHeaders(List.of("*"));
        conf.setAllowCredentials(true);
        conf.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", conf);
        return source;
    }
}
