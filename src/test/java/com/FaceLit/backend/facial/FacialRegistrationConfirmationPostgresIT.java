package com.FaceLit.backend.facial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import com.FaceLit.backend.notification.dto.request.CreateNotificationRequestDTO;
import com.FaceLit.backend.notification.dto.request.MonolithEventRequestDTO;
import com.FaceLit.backend.notification.dto.response.NotificationResponseDTO;
import com.FaceLit.backend.notification.service.NotificationService;

import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs the confirmation SQL against the PostgreSQL schema produced by Liquibase.
 *
 * The test is enabled by the CI workflow (or locally) when FACELIT_DB_URL is set.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "FACELIT_DB_URL", matches = ".+")
class FacialRegistrationConfirmationPostgresIT {
    private JdbcTemplate jdbc;
    private MockMvc mvc;
    private UUID registeredUser;
    private UUID userWithoutFace;

    @BeforeAll
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
            env("FACELIT_DB_URL"),
            envOrDefault("FACELIT_DB_USER", "facelit_user"),
            envOrDefault("FACELIT_DB_PASSWORD", "facelit_password"));
        jdbc = new JdbcTemplate(dataSource);
        TransactionTemplate transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        NotificationService notifications = new JdbcNotificationService(jdbc);
        mvc = MockMvcBuilders.standaloneSetup(new FacialEnrollmentController(
            jdbc, transactions, null, notifications,
            new com.FaceLit.backend.facial.service.FacialRegistrationService(
                new com.FaceLit.backend.facial.repository.FacialRegistrationStatusRepository(jdbc), transactions, notifications)))
            .setCustomArgumentResolvers(new AuthenticatedUserResolver())
            .build();

        registeredUser = seedUser(true);
        userWithoutFace = seedUser(false);
    }

    @AfterAll
    void tearDown() {
        if (jdbc == null) return;
        jdbc.update("DELETE FROM notification.notification WHERE id_user_app IN (?, ?)", registeredUser, userWithoutFace);
        jdbc.update("DELETE FROM facialrecognition.user_face WHERE id_apprentice IN "
            + "(SELECT id_apprentice FROM academic.apprentice WHERE id_user_app IN (?, ?))", registeredUser, userWithoutFace);
        jdbc.update("DELETE FROM academic.apprentice WHERE id_user_app IN (?, ?)", registeredUser, userWithoutFace);
        jdbc.update("DELETE FROM security.user_app WHERE id_user_app IN (?, ?)", registeredUser, userWithoutFace);
    }

    @Test
    void firstAndRepeatedConfirmationAreSuccessfulAndCreateOneNotification() throws Exception {
        MockHttpServletResponse first = confirm(registeredUser).andExpect(status().isOk()).andReturn().getResponse();
        MockHttpServletResponse repeated = confirm(registeredUser).andExpect(status().isOk()).andReturn().getResponse();

        assertTrue(first.getContentAsString().contains("\"confirmed\":true"));
        assertTrue(repeated.getContentAsString().contains("\"confirmed\":true"));
        assertEquals(1, countNotifications(registeredUser));
    }

    @Test
    void confirmationWithoutActiveFaceReturnsConflictAndCreatesNoNotification() throws Exception {
        confirm(userWithoutFace).andExpect(status().isConflict());
        assertEquals(0, countNotifications(userWithoutFace));
    }

    private org.springframework.test.web.servlet.ResultActions confirm(UUID user) throws Exception {
        return mvc.perform(post("/api/facial/registration-confirmation")
            .requestAttr("authenticated-user", user));
    }

    private UUID seedUser(boolean withFace) {
        UUID user = UUID.randomUUID();
        UUID apprentice = UUID.randomUUID();
        jdbc.update("INSERT INTO security.user_app "
            + "(id_user_app, first_name, last_name, account_status, number_document) VALUES (?, 'Test', 'Face', 'ACTIVE', ?)",
            user, "IT-" + user.toString().substring(0, 10));
        jdbc.update("INSERT INTO academic.apprentice (id_apprentice, id_user_app) VALUES (?, ?)", apprentice, user);
        if (withFace) {
            jdbc.update("INSERT INTO facialrecognition.user_face "
                + "(id_apprentice, embedding, embedding_dimension, model_name, status, created_by) "
                + "VALUES (?, ?::vector, 3, 'integration-test', 'ACTIVE', ?)",
                apprentice, "[0.1,0.2,0.3]", user.toString());
        }
        return user;
    }

    private int countNotifications(UUID user) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM notification.notification "
            + "WHERE id_user_app = ? AND notification_type = 'facial_registration_completed'", Integer.class, user);
    }

    private static String env(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required");
        return value;
    }

    private static String envOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static final class AuthenticatedUserResolver implements HandlerMethodArgumentResolver {
        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(AuthenticationPrincipal.class)
                && UUID.class.equals(parameter.getParameterType());
        }

        @Override
        public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                NativeWebRequest request, WebDataBinderFactory binderFactory) {
            return request.getAttribute("authenticated-user", NativeWebRequest.SCOPE_REQUEST);
        }
    }

    private static final class JdbcNotificationService implements NotificationService {
        private final JdbcTemplate jdbc;

        private JdbcNotificationService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

        @Override
        public NotificationResponseDTO createForRecipient(CreateNotificationRequestDTO request) {
            jdbc.update("INSERT INTO notification.notification "
                + "(id_user_app, notification_type, category, title, message, channel, reference_entity, reference_id, metadata_json) "
                + "VALUES (?, ?, 'FACIAL_RECOGNITION', ?, ?, 'APP', ?, ?, ?)",
                request.recipientUserId(), request.type(), request.title(), request.message(),
                request.referenceEntity(), request.referenceId(), request.metadataJson());
            return null;
        }

        @Override public List<NotificationResponseDTO> findForUser(UUID userId) { return List.of(); }
        @Override public NotificationResponseDTO markRead(UUID userId, UUID notificationId) { return null; }
        @Override public List<NotificationResponseDTO> markAllRead(UUID userId) { return List.of(); }
        @Override public List<NotificationResponseDTO> createCoordinatorEvent(MonolithEventRequestDTO request) { return List.of(); }
        @Override public List<NotificationResponseDTO> createIndependentCoordinatorEvent(MonolithEventRequestDTO request) { return List.of(); }
    }
}
