//package com.back.global.ai.config;
//
//import com.zaxxer.hikari.HikariDataSource;
//import org.springframework.context.annotation.*;
//import org.testcontainers.containers.PostgreSQLContainer;
//
//import javax.sql.DataSource;
//
//@Configuration
//@Profile("test")
//public class PgVectorContainerConfig {
//
//    @Bean(initMethod = "start", destroyMethod = "stop")
//    public PostgreSQLContainer<?> pgContainer() {
//        // 여기서 이미지 고정
//        PostgreSQLContainer<?> c = new PostgreSQLContainer<>("pgvector/pgvector:pg16");
//        // 필요하면 파라미터 추가
//        // c.withReuse(true);
//        return c;
//    }
//
//    @Bean
//    public DataSource dataSource(PostgreSQLContainer<?> pg) {
//        HikariDataSource ds = new HikariDataSource();
//        ds.setJdbcUrl(pg.getJdbcUrl());
//        ds.setUsername(pg.getUsername());
//        ds.setPassword(pg.getPassword());
//        // 드라이버는 Hikari가 JDBC URL로 자동 판단 (org.postgresql.Driver)
//        return ds;
//    }
//}