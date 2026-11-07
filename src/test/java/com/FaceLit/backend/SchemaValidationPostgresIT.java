package com.FaceLit.backend;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Validates all JPA entities against a disposable database built by the real Liquibase changelog. */
@EnabledIfEnvironmentVariable(named="FACELIT_DB_URL", matches=".+")
@SpringBootTest(properties={
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
    "spring.datasource.driver-class-name=org.postgresql.Driver",
    "spring.liquibase.enabled=false",
    "spring.jpa.show-sql=false",
    "jwt.secret=review-only-jwt-signing-key-not-for-production-2026",
    "spring.mail.username=qa@example.invalid",
    "spring.mail.password=review-only-mail-password",
    "facial.embedding.api-key=review-only-internal-service-key"
})
class SchemaValidationPostgresIT {
    @DynamicPropertySource static void postgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",()->System.getenv("FACELIT_DB_URL"));
        registry.add("spring.datasource.username",()->System.getenv("FACELIT_DB_USER"));
        registry.add("spring.datasource.password",()->System.getenv("FACELIT_DB_PASSWORD"));
    }
    @Test void allEntitiesMatchMigratedSchema() { }
}
