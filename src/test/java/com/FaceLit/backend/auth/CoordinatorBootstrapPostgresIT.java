package com.FaceLit.backend.auth;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Map;
import java.util.UUID;
import com.FaceLit.backend.auth.dto.request.security.CoordinatorBootstrapRequestDTO;
import com.FaceLit.backend.auth.repository.security.UserRepository;
import com.FaceLit.backend.auth.service.security.CoordinatorBootstrapService;
import com.FaceLit.backend.shared.service.EmailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Runs only on the two disposable databases created by test-coordinator-bootstrap.ps1. */
@EnabledIfEnvironmentVariable(named = "FACELIT_BOOTSTRAP_POSTGRES_TEST", matches = "1")
@SpringBootTest(properties = {
        "spring.datasource.url=${FACELIT_BOOTSTRAP_TEST_URL}", "spring.datasource.username=bootstrap_test",
        "spring.datasource.password=bootstrap-test-local-only", "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.show-sql=false", "spring.mail.username=test@example.invalid", "spring.mail.password=unused",
        "jwt.secret=bootstrap-postgres-test-secret-at-least-thirty-two-characters",
        "facelit.bootstrap.coordinator.enabled=true", "facelit.bootstrap.coordinator.exit-after=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CoordinatorBootstrapPostgresIT {
    @Autowired CoordinatorBootstrapService bootstrap;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired EntityManager em;
    @MockitoBean EmailService email;

    @Test void startupCreatesOrRotatesOneCoordinatorAndCanAuthenticate() throws Exception {
        var user = users.findByDocumentNumber(value("FACELIT_COORDINATOR_DOCUMENT")).orElseThrow();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM roleandpermission.user_role ur JOIN roleandpermission.role r ON r.id_role=ur.id_role WHERE r.role_name='COORDINATOR'",Integer.class)).isEqualTo(1);
        assertThat(encoder.matches(value("FACELIT_COORDINATOR_PASSWORD"),user.getCredential().getPassword())).isTrue();
        mvc.perform(post("/api/auth/login").contentType("application/json").content(json.writeValueAsString(Map.of(
                "numberDocument",value("FACELIT_COORDINATOR_DOCUMENT"),"password",value("FACELIT_COORDINATOR_PASSWORD"),"privacyAccepted",true))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("COORDINATOR"))
                .andExpect(jsonPath("$.userId").value(user.getIdUser().toString()));
    }

    @Test void repetitionDoesNotReplaceIdentityOrRehashTheSamePassword() {
        var user = users.findByDocumentNumber(value("FACELIT_COORDINATOR_DOCUMENT")).orElseThrow();
        String previousHash = user.getCredential().getPassword(); UUID id = user.getIdUser();
        var input = new CoordinatorBootstrapRequestDTO(value("FACELIT_COORDINATOR_DOCUMENT"),null,null,
                value("FACELIT_COORDINATOR_EMAIL"),value("FACELIT_COORDINATOR_PASSWORD"),true);
        assertThat(bootstrap.initialize(input)).isEqualTo(CoordinatorBootstrapService.Outcome.UNCHANGED);
        assertThat(users.findById(id).orElseThrow().getCredential().getPassword().equals(previousHash)).isTrue();
    }

    @Test void freshDatabaseHasNoAccountSeedAndUpgradePreservesHistoricalChecksumAndRevokesOldAccess() {
        int history = jdbc.queryForObject("SELECT count(*) FROM databasechangelog WHERE id='02-seed-coordinator'",Integer.class);
        if (legacy()) {
            assertThat(history).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT md5sum FROM databasechangelog WHERE id='02-seed-coordinator'",String.class))
                    .isEqualTo(value("FACELIT_BOOTSTRAP_LEGACY_CHECKSUM"));
            var user = users.findByDocumentNumber(value("FACELIT_COORDINATOR_DOCUMENT")).orElseThrow();
            assertThat(user.getIdUser().toString()).isEqualTo(value("FACELIT_BOOTSTRAP_LEGACY_ID"));
            assertThat(user.getCreatedBy()).isEqualTo("liquibase-seed");
            assertThat(user.getCredential().getPassword().equals(value("FACELIT_BOOTSTRAP_LEGACY_HASH"))).isFalse();
            assertThat(jdbc.queryForObject("SELECT session_status FROM security.user_session WHERE id_user_session=?",String.class,UUID.fromString(value("FACELIT_BOOTSTRAP_LEGACY_SESSION")))).isEqualTo("INACTIVE");
            assertThat(jdbc.queryForObject("SELECT used FROM security.password_recovery WHERE id_user_app=? AND token='987654'",Boolean.class,user.getIdUser())).isTrue();
        } else {
            assertThat(history).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM security.user_app WHERE created_by='liquibase-seed'",Integer.class)).isZero();
        }
        bootstrap.verifyNoPublishedCredential();
    }

    @Test @Transactional void knownPublishedCredentialIsRejectedButNormalCredentialsAreAccepted() {
        bootstrap.verifyNoPublishedCredential();
        if (legacy()) {
            jdbc.update("UPDATE security.credential SET password_hash=? WHERE id_user_app=?",value("FACELIT_BOOTSTRAP_LEGACY_HASH"),UUID.fromString(value("FACELIT_BOOTSTRAP_LEGACY_ID")));
            em.clear();
            assertThatThrownBy(bootstrap::verifyNoPublishedCredential).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("rotación");
        }
    }
    private static boolean legacy() { return "upgrade".equals(value("FACELIT_BOOTSTRAP_TEST_MODE")); }
    private static String value(String key) { return System.getenv(key); }
}
