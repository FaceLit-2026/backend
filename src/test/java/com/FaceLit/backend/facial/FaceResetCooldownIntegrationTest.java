package com.FaceLit.backend.facial;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.FaceLit.backend.academic.model.academic.*;
import com.FaceLit.backend.academic.model.enums.AcademicState;
import com.FaceLit.backend.academic.repository.UserChipRepository;
import com.FaceLit.backend.auth.model.enums.AccountStatus;
import com.FaceLit.backend.auth.model.security.User;
import com.FaceLit.backend.auth.repository.security.UserRepository;
import com.FaceLit.backend.facial.model.FaceResetRequest;
import com.FaceLit.backend.facial.repository.FaceResetRequestRepository;
import com.FaceLit.backend.notification.repository.NotificationRepository;
import com.FaceLit.backend.shared.service.UserDisplayNameService;
import com.FaceLit.backend.shared.service.EmailService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:face_reset_cooldown;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;DATABASE_TO_LOWER=TRUE;LOCK_TIMEOUT=60000")
class FaceResetCooldownIntegrationTest {
    private static final String LOCK = "SELECT id_user_app FROM security.user_app WHERE id_user_app = ? FOR UPDATE";
    @Autowired FaceResetService service;
    @Autowired FaceResetRequestRepository requests;
    @Autowired UserRepository users;
    @Autowired NotificationRepository notifications;
    @Autowired TransactionTemplate transactions;
    @Autowired UserDisplayNameService names;
    @MockitoSpyBean JdbcTemplate jdbc;
    @MockitoBean UserChipRepository assignments;
    @MockitoBean FaceResetNotificationDelivery delivery;
    @MockitoBean EmailService email;
    private UUID userId;

    @BeforeEach void setup() {
        User user = new User(); user.setFirstName("Ana"); user.setLastName("Prueba");
        user.setDocumentNumber("9" + Math.abs(UUID.randomUUID().getMostSignificantBits() % 1000000000L));
        user.setAccountStatus(AccountStatus.ACTIVE);
        userId = transactions.execute(tx -> users.saveAndFlush(user).getIdUser());
        // Only enrollment eligibility is stubbed; user rows, request rows and locks are real H2 operations.
        doReturn(true).when(jdbc).queryForObject(contains("SELECT EXISTS"), eq(Boolean.class), eq(userId));
        Apprentice apprentice = new Apprentice(); apprentice.setUser(user);
        Program program = new Program(); program.setProgramName("Software");
        Chip chip = new Chip(); chip.setChipCode("3145555"); chip.setProgram(program);
        UserChip assignment = new UserChip(); assignment.setApprentice(apprentice); assignment.setChip(chip);
        when(assignments.findByApprentice_User_IdUserAndState(userId, AcademicState.ACTIVE)).thenReturn(Optional.of(assignment));
    }

    @AfterEach void cleanTestRows() {
        transactions.executeWithoutResult(tx -> {
            requests.deleteAll(requests.findAll().stream().filter(row -> userId.equals(row.getUserId())).toList());
            requests.flush(); users.deleteById(userId);
        });
    }

    @Test void concurrentRequestsSerializeOnTheSameUserAndOnlyOneIsCreated() throws Exception {
        var firstLocked = new CountDownLatch(1);
        var secondAttempted = new CountDownLatch(1);
        var releaseFirst = new CountDownLatch(1);
        var calls = new AtomicInteger();
        doAnswer(call -> {
            if (calls.incrementAndGet() == 1) {
                Object result = call.callRealMethod();
                firstLocked.countDown();
                if (!releaseFirst.await(60, TimeUnit.SECONDS)) throw new IllegalStateException("Test lock timeout");
                return result;
            }
            secondAttempted.countDown();
            return call.callRealMethod();
        }).when(jdbc).queryForObject(eq(LOCK), eq(UUID.class), eq(userId));
        var pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = pool.submit(this::createStatus);
            assertThat(firstLocked.await(60, TimeUnit.SECONDS)).isTrue();
            Future<Integer> second = pool.submit(this::createStatus);
            assertThat(secondAttempted.await(60, TimeUnit.SECONDS)).isTrue();
            assertThat(second.isDone()).isFalse();
            releaseFirst.countDown();
            assertThat(List.of(first.get(60, TimeUnit.SECONDS), second.get(60, TimeUnit.SECONDS)))
                    .containsExactly(200, 429);
            assertThat(requests.count()).isEqualTo(1);
            assertThat(notifications.count()).isZero();
            verifyNoInteractions(email);
        } finally {
            releaseFirst.countDown(); pool.shutdownNow();
        }
    }

    @Test void newServiceInstanceReadsPersistedCooldownAfterApprovalAndReregistration() {
        var created = service.create(userId);
        OffsetDateTime sentAt = transactions.execute(tx -> {
            var row = requests.findById(created.requestId()).orElseThrow();
            row.setStatus("ACCEPTED"); row.setResolvedAt(OffsetDateTime.now());
            row.setConsumedAt(OffsetDateTime.now()); requests.saveAndFlush(row);
            return row.getRequestedAt();
        });
        var anotherInstance = new FaceResetService(requests, assignments, notifications, jdbc, 24, names);
        assertThatThrownBy(() -> transactions.execute(tx -> anotherInstance.create(userId)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(429));
        var stored = requests.findById(created.requestId()).orElseThrow();
        assertThat(stored.getRequestedAt().toInstant()).isEqualTo(sentAt.toInstant());
        assertThat(stored.getConsumedAt()).isNotNull();
        assertThat(requests.count()).isEqualTo(1);
        assertThat(notifications.count()).isZero();
    }

    private int createStatus() {
        try { service.create(userId); return 200; }
        catch (ResponseStatusException ex) { return ex.getStatusCode().value(); }
    }
}
