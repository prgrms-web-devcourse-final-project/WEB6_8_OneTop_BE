/**
 * [RUNNER] SqlResourceRunner
 * - 클래스패스 SQL 파일을 DataSource로 실행하여 스키마/데이터를 자동 반영
 * - 사람 손으로 에디터에서 실행할 필요 없이 기동 시 자동 처리
 */
package com.back.global.migration;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Component
@Profile({"local","dev"})
@RequiredArgsConstructor
@Slf4j
public class SqlResourceRunner {

    private final DataSource dataSource;

    // 기동 시 SQL 리소스 실행
    @PostConstruct
    public void run() {
        try {
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
            // 예: /db/manual/V20251002__dvcs_schema.sql
            populator.addScript(new ClassPathResource("db/manual/V20251002__dvcs_schema.sql"));
            populator.setIgnoreFailedDrops(true);
            populator.execute(dataSource);
            log.info("[SQL-RESOURCE] executed: db/manual/V20251002__dvcs_schema.sql");
        } catch (Exception e) {
            log.error("[SQL-RESOURCE] execution failed", e);
        }
    }
}
