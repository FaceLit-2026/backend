package com.FaceLit.backend.academic;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Runs the same manual/CSV audit cases on a disposable Liquibase database. */
@EnabledIfEnvironmentVariable(named = "FACELIT_DB_URL", matches = ".+")
class AcademicAuditPostgresIT extends AcademicAuditIntegrationTest {
    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("FACELIT_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("FACELIT_DB_USER"));
        registry.add("spring.datasource.password", () -> System.getenv("FACELIT_DB_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "2");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }
}
