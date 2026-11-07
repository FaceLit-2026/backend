package com.FaceLit.backend.auth;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.FaceLit.backend.auth.model.security.*;
import com.FaceLit.backend.auth.model.roleandpermission.*;
import com.FaceLit.backend.auth.model.enums.*;
import com.FaceLit.backend.auth.repository.security.*;
import com.FaceLit.backend.auth.repository.roleandpermission.*;
import com.FaceLit.backend.auth.service.roleandpermission.JwtService;
import com.FaceLit.backend.auth.service.serviceImpl.security.*;
import com.FaceLit.backend.shared.service.EmailService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RecoverySessionSecurityIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired TransactionTemplate tx;
    @Autowired UserRepository users;
    @Autowired CredentialRepository credentials;
    @Autowired PasswordRecoveryRepository recoveries;
    @Autowired AuthRateLimitRepository limits;
    @Autowired UserSessionRepository sessions;
    @Autowired RoleRepository roles;
    @Autowired UserRoleRepository userRoles;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtService jwt;
    @Autowired RecoveryChallengeService challenges;
    @Autowired SessionSecurityService sessionSecurity;
    @Autowired com.FaceLit.backend.auth.service.serviceImpl.security.UserManagementServiceImpl management;
    @Autowired com.FaceLit.backend.auth.service.roleandpermission.AdminRoleService roleService;
    @MockitoBean EmailService email;
    UUID userId;
    String address;
    String code;
    @BeforeEach void setup() {
        limits.deleteAll();
        address = UUID.randomUUID() + "@example.com";
        code = "314159";
        userId = tx.execute(status -> {
            Role role = roles.findByNameRole(RoleName.APPRENTICE).orElseGet(() -> {
                Role r = new Role(); r.setNameRole(RoleName.APPRENTICE); return roles.saveAndFlush(r);
            });
            User u = new User(); u.setFirstName("Seguridad"); u.setLastName("Prueba");
            u.setDocumentNumber(Long.toUnsignedString(UUID.randomUUID().getMostSignificantBits()).substring(0, 10));
            u.setAccountStatus(AccountStatus.ACTIVE); users.saveAndFlush(u);
            Credential c = new Credential(); c.setUser(u); c.setEmail(address); c.setPassword(encoder.encode("Anterior1!"));
            c.setCredentialStatus(CredentialStatus.ACTIVE); credentials.saveAndFlush(c); u.setCredential(c);
            UserRole ur = new UserRole(); ur.setUser(u); ur.setRole(role); ur.setAssignedAt(OffsetDateTime.now()); userRoles.saveAndFlush(ur);
            PasswordRecovery r = new PasswordRecovery(); r.setUser(u); r.setToken(code);
            r.setDestinationEmail(address); r.setExpirationDate(OffsetDateTime.now().plusMinutes(5)); recoveries.saveAndFlush(r);
            return u.getIdUser();
        });
    }
    String verifyCode(String account, String value, int expected) throws Exception {
        String body = mvc.perform(post("/api/auth/verify-token").contentType("application/json")
                .content(json.writeValueAsString(Map.of("email", account, "token", value))))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
        return expected == 200 ? json.readTree(body).get("resetToken").asText() : body;
    }
    void resetWith(String token, int expected) throws Exception {
        mvc.perform(post("/api/auth/reset-password").contentType("application/json")
                .content(json.writeValueAsString(Map.of("email", address, "token", token,
                    "newPassword", "NuevaClave1!", "confirmPassword", "NuevaClave1!"))))
                .andExpect(status().is(expected));
    }
    String token() {
        return tx.execute(status -> jwt.generateToken(users.findById(userId).orElseThrow(), "APPRENTICE", List.of()));
    }
    @Test void codeForAnotherAccountIsRejected() throws Exception {
        String otherAddress = UUID.randomUUID() + "@example.com";
        tx.executeWithoutResult(status -> {
            User other = new User(); other.setFirstName("Otra"); other.setLastName("Cuenta");
            other.setDocumentNumber(UUID.randomUUID().toString().replace("-", "").substring(0, 10));
            other.setAccountStatus(AccountStatus.ACTIVE); users.saveAndFlush(other);
            Credential c = new Credential(); c.setUser(other); c.setEmail(otherAddress); c.setPassword(encoder.encode("OtraClave1!"));
            c.setCredentialStatus(CredentialStatus.ACTIVE); credentials.saveAndFlush(c);
            PasswordRecovery r = new PasswordRecovery(); r.setUser(other); r.setToken("271828");
            r.setDestinationEmail(otherAddress); r.setExpirationDate(OffsetDateTime.now().plusMinutes(5)); recoveries.saveAndFlush(r);
        });
        verifyCode(otherAddress, code, 400);
        assertThat(recoveries.findFirstByUser_IdUserAndUsedFalseOrderByCreatedAtDesc(userId)).isPresent();
    }
    @Test void fifthFailurePersistsAndBlocksCorrectCodeAndResend() throws Exception {
        for (int i = 0; i < 4; i++) verifyCode(address, "000000", 400);
        verifyCode(address, "000000", 429); verifyCode(address, code, 429);
        assertThat(recoveries.findFirstByUser_IdUserAndUsedFalseOrderByCreatedAtDesc(userId).orElseThrow().getVerificationAttempts()).isEqualTo(5);
        mvc.perform(post("/api/auth/request-recovery").contentType("application/json")
            .content(json.writeValueAsString(Map.of("email", address)))).andExpect(status().isTooManyRequests())
            .andExpect(header().exists("Retry-After"));
    }
    @Test void expiredCodeCannotAuthorizeReset() throws Exception {
        tx.executeWithoutResult(status -> {
            var r = recoveries.findFirstByUser_IdUserAndUsedFalseOrderByCreatedAtDesc(userId).orElseThrow();
            r.setExpirationDate(OffsetDateTime.now().minusSeconds(1)); recoveries.saveAndFlush(r);
        });
        verifyCode(address, code, 410);
    }
    @Test void resetRequiresLongSingleUseAuthorizationAndRevokesOldToken() throws Exception {
        String old = token();
        resetWith(code, 400);
        String authorization = verifyCode(address, code, 200);
        assertThat(authorization).hasSize(43);
        assertThat(recoveries.findFirstByUser_IdUserAndUsedFalseOrderByCreatedAtDesc(userId).orElseThrow().getResetTokenHash())
            .isNotEqualTo(authorization);
        resetWith(authorization, 200); resetWith(authorization, 400);
        assertThat(encoder.matches("NuevaClave1!", credentials.findByUser_IdUser(userId).orElseThrow().getPassword())).isTrue();
        assertThat(jwt.validateToken(old)).isFalse();
    }
    @Test void logoutInvalidatesOnlyTheSelectedSessionAndNewLoginCannotReviveIt() throws Exception {
        String first = token(); String second = token();
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + first)).andExpect(status().isNoContent());
        assertThat(jwt.validateToken(first)).isFalse(); assertThat(jwt.validateToken(second)).isTrue();
        token(); assertThat(jwt.validateToken(first)).isFalse();
        mvc.perform(get("/api/profile/me").header("Authorization", "Bearer " + first)).andExpect(status().isUnauthorized());
    }
    @Test void inactiveAccountRejectsJwt() throws Exception {
        String old = token();
        tx.executeWithoutResult(status -> {
            User u = users.findById(userId).orElseThrow();
            var dto = new com.FaceLit.backend.auth.dto.request.security.UpdateUserRequestDTO();
            dto.setNumberDocument(u.getDocumentNumber()); dto.setFirstName(u.getFirstName()); dto.setLastName(u.getLastName());
            dto.setEmail(address); dto.setRole(RoleName.APPRENTICE); dto.setAccountStatus(AccountStatus.INACTIVE);
            management.updateUser(userId, dto); dto.setAccountStatus(AccountStatus.ACTIVE); management.updateUser(userId, dto);
        });
        assertThat(jwt.validateToken(old)).isFalse();
        mvc.perform(get("/api/profile/me").header("Authorization", "Bearer " + old))
            .andExpect(status().isUnauthorized());
    }
    @Test void roleChangeRevokesOldSessionEvenIfRoleIsRestored() throws Exception {
        String old = token();
        tx.executeWithoutResult(status -> {
            Role role = roles.findByNameRole(RoleName.INSTRUCTOR).orElseGet(() -> { Role r = new Role(); r.setNameRole(RoleName.INSTRUCTOR); return roles.saveAndFlush(r); });
            var dto = new com.FaceLit.backend.auth.dto.request.roleandpermission.AssignRoleRequestDTO(); dto.setRole(RoleName.INSTRUCTOR);
            roleService.assignRole(userId, dto, null); dto.setRole(RoleName.APPRENTICE); roleService.assignRole(userId, dto, null);
        });
        assertThat(jwt.validateToken(old)).isFalse();
        mvc.perform(get("/api/profile/me").header("Authorization", "Bearer " + old))
            .andExpect(status().isUnauthorized());
    }
    @Test void concurrentPasswordResetsConsumeAuthorizationOnlyOnce() throws Exception {
        String old = token();
        String authorization = verifyCode(address, code, 200);
        String payload = json.writeValueAsString(Map.of("email", address, "token", authorization,
            "newPassword", "NuevaClave1!", "confirmPassword", "NuevaClave1!"));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CyclicBarrier start = new CyclicBarrier(2);
        Callable<Integer> reset = () -> {
            start.await(10, TimeUnit.SECONDS);
            return mvc.perform(post("/api/auth/reset-password").contentType("application/json")
                .content(payload)).andReturn().getResponse().getStatus();
        };
        try {
            List<Future<Integer>> results = pool.invokeAll(List.of(reset, reset), 30, TimeUnit.SECONDS);
            assertThat(List.of(results.get(0).get(), results.get(1).get()))
                .containsExactlyInAnyOrder(200, 400);
            assertThat(recoveries.findAllByUser_IdUser(userId)).singleElement().satisfies(recovery -> {
                assertThat(recovery.isUsed()).isTrue();
                assertThat(recovery.getResetTokenHash()).isNull();
            });
            assertThat(encoder.matches("NuevaClave1!", credentials.findByUser_IdUser(userId)
                .orElseThrow().getPassword())).isTrue();
            mvc.perform(get("/api/profile/me").header("Authorization", "Bearer " + old))
                .andExpect(status().isUnauthorized());
        } finally {
            pool.shutdownNow();
        }
    }
    @Test void loginCreatesExactlyOneRevocableSession() throws Exception {
        String document = users.findById(userId).orElseThrow().getDocumentNumber();
        String result = mvc.perform(post("/api/auth/login").contentType("application/json")
            .content(json.writeValueAsString(Map.of("numberDocument", document, "password", "Anterior1!", "privacyAccepted", true))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(jwt.validateToken(json.readTree(result).get("token").asText())).isTrue();
        assertThat(sessions.findByUser_IdUser(userId)).hasSize(1);
    }
    @Test void anonymousLogoutIsUnauthorized() throws Exception {
        mvc.perform(post("/api/auth/logout")).andExpect(status().isUnauthorized());
    }
    @Test void currentPasswordCanBeChosenAgainAsRequested() throws Exception {
        String authorization = verifyCode(address, code, 200);
        mvc.perform(post("/api/auth/reset-password").contentType("application/json")
            .content(json.writeValueAsString(Map.of("email", address, "token", authorization,
                "newPassword", "Anterior1!", "confirmPassword", "Anterior1!")))).andExpect(status().isOk());
        assertThat(encoder.matches("Anterior1!", credentials.findByUser_IdUser(userId).orElseThrow().getPassword())).isTrue();
    }
    @Test void concurrentWrongCodesCannotLoseAttempts() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(5);
        try {
            List<Callable<Void>> jobs = new ArrayList<>();
            for (int i = 0; i < 5; i++) jobs.add(() -> {
                try { challenges.verify(address, "000000"); fail("wrong code accepted"); }
                catch (com.FaceLit.backend.auth.exception.RecoverySecurityException expected) { }
                return null;
            });
            for (Future<Void> result : pool.invokeAll(jobs)) result.get(10, TimeUnit.SECONDS);
            assertThat(recoveries.findFirstByUser_IdUserAndUsedFalseOrderByCreatedAtDesc(userId).orElseThrow().getVerificationAttempts()).isEqualTo(5);
        } finally { pool.shutdownNow(); }
    }
    @Test void ipLimitReturns429AndDoesNotTrustForwardedHeader() throws Exception {
        for (int i = 0; i < 30; i++) {
            mvc.perform(post("/api/auth/verify-token").header("X-Forwarded-For", "203.0.113." + i)
                .contentType("application/json").content("{}"));
        }
        mvc.perform(post("/api/auth/verify-token").header("X-Forwarded-For", "198.51.100.1")
            .contentType("application/json").content("{}")).andExpect(status().isTooManyRequests())
            .andExpect(header().exists("Retry-After"));
    }
    @Test void recoverySendingLimitsPersistAcrossRequests() throws Exception {
        String request = json.writeValueAsString(Map.of("email", address));
        mvc.perform(post("/api/auth/request-recovery").contentType("application/json").content(request))
            .andExpect(status().isOk());
        mvc.perform(post("/api/auth/request-recovery").contentType("application/json").content(request))
            .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
        String minuteKey = com.FaceLit.backend.auth.service.security.RecoverySecrets.digest("request-minute:" + address);
        for (int i = 0; i < 2; i++) {
            tx.executeWithoutResult(status -> {
                var minute = limits.findById(minuteKey).orElseThrow();
                minute.setWindowStart(OffsetDateTime.now().minusMinutes(2)); limits.saveAndFlush(minute);
            });
            mvc.perform(post("/api/auth/request-recovery").contentType("application/json").content(request))
                .andExpect(status().isOk());
        }
        tx.executeWithoutResult(status -> {
            var minute = limits.findById(minuteKey).orElseThrow();
            minute.setWindowStart(OffsetDateTime.now().minusMinutes(2)); limits.saveAndFlush(minute);
        });
        mvc.perform(post("/api/auth/request-recovery").contentType("application/json").content(request))
            .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
        String windowKey = com.FaceLit.backend.auth.service.security.RecoverySecrets.digest("request-window:" + address);
        assertThat(limits.findById(windowKey).orElseThrow().getAttempts()).isEqualTo(3);
    }
}
