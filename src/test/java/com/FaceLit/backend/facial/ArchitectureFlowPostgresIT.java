package com.FaceLit.backend.facial;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.FaceLit.backend.shared.service.EmailService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.LinkedMultiValueMap;

/**
 * Opt-in HTTP integration across Spring, the FastAPI fixture and a Liquibase-built PostgreSQL.
 * The fixture substitutes inference only; liveness rules, SQL, JWT and attendance stay real.
 * Run only with the disposable database described in README.
 */
@EnabledIfEnvironmentVariable(named = "FACELIT_ARCH_E2E", matches = "1")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = {
        "server.port=18080",
        "server.address=127.0.0.1",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:55439/facelit_arch",
        "spring.datasource.username=facelit_arch",
        "spring.datasource.password=architecture-e2e-local-only",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.mail.username=architecture@example.invalid",
        "spring.mail.password=unused",
        "jwt.secret=architecture-local-e2e-secret-at-least-thirty-two-characters",
        "facial.embedding.url=http://127.0.0.1:18090",
        "facial.embedding.api-key=architecture-test-key",
        "facelit.allowed-origins=http://127.0.0.1:18081"
})
class ArchitectureFlowPostgresIT {
    private static final String PASSWORD = "Architecture1!";
    private final UUID learner = UUID.randomUUID();
    private final UUID teacher = UUID.randomUUID();
    private final UUID coordinator = UUID.randomUUID();
    private final UUID apprentice = UUID.randomUUID();
    private final UUID instructor = UUID.randomUUID();
    private final UUID program = UUID.randomUUID();
    private final UUID chip = UUID.randomUUID();
    private final UUID environment = UUID.randomUUID();
    private final UUID device = UUID.randomUUID();
    private final UUID session = UUID.randomUUID();

    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired TestRestTemplate http;
    @LocalServerPort int port;
    @MockitoBean EmailService email;

    @BeforeAll
    void seedDisposableDatabase() {
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).isEqualTo("facelit_arch");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM databasechangelog", Integer.class)).isGreaterThanOrEqualTo(43);
        user(learner, "98765001", "Learner", "APPRENTICE");
        user(teacher, "98765002", "Teacher", "INSTRUCTOR");
        user(coordinator, "98765003", "Coordinator", "COORDINATOR");
        jdbc.update("INSERT INTO academic.program(id_program, program_name, program_code, state) VALUES (?, 'Architecture Fixture', 'ARCHFIX', 'ACTIVE')", program);
        jdbc.update("INSERT INTO academic.chip(id_chip, id_program, chip_code, state) VALUES (?, ?, '9876501', 'ACTIVE')", chip, program);
        jdbc.update("INSERT INTO academic.instructor(id_instructor, id_user_app, instructor_type) VALUES (?, ?, 'SPECIFIC')", instructor, teacher);
        jdbc.update("INSERT INTO academic.instructor_program(id_instructor, id_program) VALUES (?, ?)", instructor, program);
        jdbc.update("INSERT INTO academic.instructor_chip(id_instructor, id_chip) VALUES (?, ?)", instructor, chip);
        jdbc.update("INSERT INTO academic.apprentice(id_apprentice, id_user_app) VALUES (?, ?)", apprentice, learner);
        jdbc.update("INSERT INTO academic.apprentice_chip(id_apprentice, id_chip, state) VALUES (?, ?, 'ACTIVE')", apprentice, chip);
        jdbc.update("INSERT INTO environment.environment(id_environment, environment_name, state) VALUES (?, 'Architecture Room', 'ACTIVE')", environment);
        jdbc.update("INSERT INTO facialrecognition.device(id_device, id_environment, device_code, status) VALUES (?, ?, 'ARCH-CAMERA', 'ACTIVE')", device, environment);
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update("""
                INSERT INTO environment.record_environment(id_record_environment, id_environment, id_device,
                    id_chip, id_instructor_scheduled, entry_time, registration_minutes, exit_time, shutdown_time)
                VALUES (?, ?, ?, ?, ?, ?, 10, ?, ?)
                """, session, environment, device, chip, instructor, now.minusMinutes(1), now.plusHours(1), now.plusHours(2));
    }

    private void user(UUID id, String document, String name, String role) {
        jdbc.update("INSERT INTO security.user_app(id_user_app, first_name, last_name, number_document, account_status, initial_password_prompt_seen) VALUES (?, ?, 'Fixture', ?, 'ACTIVE', TRUE)", id, name, document);
        jdbc.update("INSERT INTO security.credential(id_user_app, email, password_hash) VALUES (?, ?, ?)", id, document + "@example.invalid", passwords.encode(PASSWORD));
        assertThat(jdbc.update("INSERT INTO roleandpermission.user_role(id_user_app, id_role) SELECT ?, id_role FROM roleandpermission.role WHERE role_name = ?", id, role)).isEqualTo(1);
    }

    private String login(String document) {
        var result = http.postForEntity("/api/auth/login", Map.of("numberDocument", document, "password", PASSWORD, "privacyAccepted", true), JsonNode.class);
        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).isNotNull();
        return result.getBody().path("token").asText();
    }

    private HttpEntity<Object> authenticated(Object body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(body, headers);
    }

    @Test
    void realHttpRegistrationAttendanceReportsAndCsvKeepTheirContracts() throws Exception {
        String learnerToken = login("98765001");
        String teacherToken = login("98765002");
        String coordinatorToken = login("98765003");
        List<String> challenges = List.of("MOVE_UP", "MOVE_DOWN", "MOVE_CLOSER", "MOVE_AWAY");
        for (int challengeIndex = 0; challengeIndex < challenges.size(); challengeIndex++) {
            String expected = challenges.get(challengeIndex);
            String different = challenges.get((challengeIndex + 1) % challenges.size());
            for (String performed : List.of(expected, "STILL", different)) {
                List<String> samples = java.util.stream.IntStream.range(0, 7)
                        .mapToObj(index -> "liveness-fixture:" + performed + ":" + index).toList();
                var checked = http.exchange("/api/facial/embeddings/liveness-check", HttpMethod.POST,
                        authenticated(Map.of("image_frames", samples, "liveness_challenge", expected), learnerToken), JsonNode.class);
                assertThat(checked.getStatusCode().value()).as("HTTP liveness %s / %s", expected, performed).isEqualTo(200);
                assertThat(checked.getBody().path("live").asBoolean())
                        .as("Requested %s; performed %s", expected, performed).isEqualTo(expected.equals(performed));
            }
        }
        for (String retired : List.of("BLINK", "OPEN_CLOSE_MOUTH", "NOD", "SHAKE_HEAD", "KISS_CAMERA", "MOVE_LEFT", "MOVE_RIGHT")) {
            List<String> samples = java.util.stream.IntStream.range(0, 7)
                    .mapToObj(index -> "liveness-fixture:MOVE_UP:" + index).toList();
            var checked = http.exchange("/api/facial/embeddings/liveness-check", HttpMethod.POST,
                    authenticated(Map.of("image_frames", samples, "liveness_challenge", retired), learnerToken), JsonNode.class);
            assertThat(checked.getStatusCode().value()).isEqualTo(200);
            assertThat(checked.getBody().path("live").asBoolean()).as("Retired challenge %s", retired).isFalse();
        }
        List<String> frames = Collections.nCopies(14, "data:image/jpeg;base64,fixture");
        Map<String, Object> registration = Map.of("user_id", learner, "image_base64", frames.get(0),
                "image_frames", frames, "liveness_challenges", List.of("MOVE_UP", "MOVE_DOWN"));
        var enrolled = http.exchange("/api/facial/embeddings/from-image", HttpMethod.POST, authenticated(registration, learnerToken), JsonNode.class);
        assertThat(enrolled.getStatusCode().value()).isEqualTo(201);
        assertThat(enrolled.getBody().has("embedding")).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM facialrecognition.user_face WHERE id_apprentice = ?", Integer.class, apprentice)).isEqualTo(1);

        for (int repeat = 0; repeat < 2; repeat++) {
            assertThat(http.exchange("/api/facial/registration-confirmation", HttpMethod.POST,
                    authenticated(Map.of(), learnerToken), JsonNode.class).getStatusCode().value()).isEqualTo(200);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification.notification WHERE id_user_app = ? AND notification_type = 'facial_registration_completed'", Integer.class, learner)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT category FROM notification.notification WHERE id_user_app = ? AND notification_type = 'facial_registration_completed'", String.class, learner)).isEqualTo("FACIAL_RECOGNITION");
        var inbox = http.exchange("/api/notifications", HttpMethod.GET, authenticated(null, learnerToken), JsonNode.class);
        assertThat(inbox.getStatusCode().value()).isEqualTo(200);
        assertThat(inbox.getBody().findValuesAsText("category")).contains("FACIAL_RECOGNITION");
        var registrationStatus = http.exchange("/api/facial/registration-status", HttpMethod.GET, authenticated(null, learnerToken), JsonNode.class);
        assertThat(registrationStatus.getBody().path("registered").asBoolean()).isTrue();

        Map<String, Object> capture = Map.of("idRecordEnvironment", session, "idDevice", device,
                "imageFrames", frames, "livenessChallenges", List.of("MOVE_UP", "MOVE_DOWN"), "origin", "PC");
        // Tomcat's existing error dispatch may report 401; both paths must reject the write.
        assertThat(http.exchange("/api/facial/events/from-image", HttpMethod.POST,
                authenticated(capture, learnerToken), JsonNode.class).getStatusCode().is4xxClientError()).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM facialrecognition.facial_event WHERE id_record_environment = ?", Integer.class, session)).isZero();
        var attendance = http.exchange("/api/facial/events/from-image", HttpMethod.POST, authenticated(capture, teacherToken), JsonNode.class);
        assertThat(attendance.getStatusCode().value()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM facialrecognition.facial_event WHERE id_record_environment = ? AND id_apprentice = ? AND deleted_at IS NULL", Integer.class, session, apprentice)).isEqualTo(1);
        assertThat(http.exchange("/api/facial/events/from-image", HttpMethod.POST,
                authenticated(capture, teacherToken), JsonNode.class).getStatusCode().value()).isEqualTo(409);

        String date = LocalDate.now(ZoneId.of("America/Bogota")).toString();
        String matrixUrl = "/api/facial/events/attendance-matrix?idChip=" + chip + "&dateFrom=" + date + "&dateTo=" + date;
        var report = http.exchange(matrixUrl, HttpMethod.GET, authenticated(null, teacherToken), JsonNode.class);
        assertThat(report.getStatusCode().value()).isEqualTo(200);
        assertThat(report.getBody().path("learners").size()).isEqualTo(1);
        assertThat(report.getBody().path("learners").get(0).path("days").get(0).path("status").asText()).isEqualTo("punctual");

        String csv = "tipo,documento,nombre,apellido,correo,programa_codigo,ficha_codigo,instructor_tipo\n"
                + "programa,,Arquitectura Prueba,,,ARCTEST,,\n"
                + "ficha,,,,,ARCTEST,9876543,\n"
                + "aprendiz,98765004,Otra,Prueba,98765004@example.invalid,,9876543,\n";
        var form = new LinkedMultiValueMap<String, Object>();
        form.add("file", new ByteArrayResource(csv.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            @Override public String getFilename() { return "architecture.csv"; }
        });
        HttpHeaders multipart = new HttpHeaders();
        multipart.setBearerAuth(coordinatorToken);
        multipart.setContentType(MediaType.MULTIPART_FORM_DATA);
        var imported = http.postForEntity("/api/academic/csv/upload", new HttpEntity<>(form, multipart), JsonNode.class);
        assertThat(imported.getStatusCode().value()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM academic.program WHERE program_code = 'ARCTEST'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM security.user_app WHERE number_document = '98765004'", Integer.class)).isEqualTo(1);

        // Optional browser verification runs while this real backend and its fixtures are alive.
        String browser = System.getenv("FACELIT_ARCH_BROWSER_SCRIPT");
        if (browser != null && !browser.isBlank()) {
            ProcessBuilder process = new ProcessBuilder(System.getenv().getOrDefault("FACELIT_ARCH_NODE", "node"), browser);
            process.environment().put("ARCH_BACKEND_URL", "http://127.0.0.1:" + port);
            process.inheritIO();
            assertThat(process.start().waitFor()).isZero();
        }
    }
}
