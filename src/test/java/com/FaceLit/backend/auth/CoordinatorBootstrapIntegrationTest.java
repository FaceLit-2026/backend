package com.FaceLit.backend.auth;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import com.FaceLit.backend.auth.dto.request.security.CoordinatorBootstrapRequestDTO;
import com.FaceLit.backend.auth.model.enums.AccountStatus;
import com.FaceLit.backend.auth.model.enums.RoleName;
import com.FaceLit.backend.auth.model.roleandpermission.Role;
import com.FaceLit.backend.auth.model.security.PasswordRecovery;
import com.FaceLit.backend.auth.model.security.User;
import com.FaceLit.backend.auth.repository.roleandpermission.RoleRepository;
import com.FaceLit.backend.auth.repository.roleandpermission.UserRoleRepository;
import com.FaceLit.backend.auth.repository.security.CredentialRepository;
import com.FaceLit.backend.auth.repository.security.PasswordRecoveryRepository;
import com.FaceLit.backend.auth.repository.security.UserRepository;
import com.FaceLit.backend.auth.service.security.CoordinatorBootstrapService;
import com.FaceLit.backend.auth.service.security.CoordinatorBootstrapService.Outcome;
import com.FaceLit.backend.shared.service.EmailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {"spring.mail.username=test@example.invalid", "spring.mail.password=unused",
        "jwt.secret=bootstrap-integration-test-secret-at-least-thirty-two-characters", "facelit.bootstrap.coordinator.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CoordinatorBootstrapIntegrationTest {
    private static final String ORIGINAL = "InitialTest1!";
    private static final String REPLACEMENT = "ChangedTest2!";
    @Autowired CoordinatorBootstrapService bootstrap;
    @Autowired UserRepository users;
    @Autowired CredentialRepository credentials;
    @Autowired RoleRepository roles;
    @Autowired UserRoleRepository assignments;
    @Autowired PasswordRecoveryRepository recoveries;
    @Autowired PasswordEncoder encoder;
    @Autowired EntityManager em;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean EmailService email;
    private String document;
    private String address;

    @BeforeEach void fixture() {
        document = String.valueOf(java.util.concurrent.ThreadLocalRandom.current().nextLong(1000000000L,9000000000L));
        address = "bootstrap-" + UUID.randomUUID() + "@example.invalid";
        if (roles.findByNameRole(RoleName.COORDINATOR).isEmpty()) {
            Role role = new Role(); role.setNameRole(RoleName.COORDINATOR); roles.saveAndFlush(role);
        }
    }

    @Test void createsOneCoordinatorWithBcryptAndAllowsRealLogin() throws Exception {
        assertThat(bootstrap.initialize(request(ORIGINAL,false))).isEqualTo(Outcome.CREATED);
        var credential = credentials.findByUser_DocumentNumber(document).orElseThrow();
        assertThat(encoder.matches(ORIGINAL,credential.getPassword())).isTrue();
        assertThat(credential.getPassword().equals(ORIGINAL)).isFalse();
        assertThat(assignments.findByUserId(credential.getUser().getIdUser()).orElseThrow().getRole().getNameRole())
                .isEqualTo(RoleName.COORDINATOR);
        login(ORIGINAL);
        assertThat(credential.getUser().isInitialPasswordPromptSeen()).isTrue();
    }

    @Test void repeatedCreationDoesNotOverwriteCredentialsOrPersonalData() {
        bootstrap.initialize(request(ORIGINAL,false));
        var user = users.findByDocumentNumber(document).orElseThrow();
        var id = user.getIdUser(); String hash = user.getCredential().getPassword();
        assertThat(bootstrap.initialize(request(ORIGINAL,false))).isEqualTo(Outcome.UNCHANGED);
        assertThat(users.findByDocumentNumber(document).orElseThrow().getIdUser()).isEqualTo(id);
        assertThat(user.getCredential().getPassword().equals(hash)).isTrue();
        assertThatThrownBy(() -> bootstrap.initialize(request(REPLACEMENT,false))).isInstanceOf(IllegalStateException.class);
        assertThat(user.getFirstName()).isEqualTo("Initial");
    }

    @Test void rotationPreservesIdentityAndRevokesJwtAndRecoveryAuthorization() throws Exception {
        bootstrap.initialize(request(ORIGINAL,false));
        String oldToken = login(ORIGINAL);
        var user = users.findByDocumentNumber(document).orElseThrow(); var id = user.getIdUser();
        PasswordRecovery recovery = new PasswordRecovery(); recovery.setUser(user); recovery.setToken("123456");
        recovery.setExpirationDate(OffsetDateTime.now().plusMinutes(5)); recovery.setResetTokenHash("a".repeat(64));
        recovery.setVerifiedAt(OffsetDateTime.now()); recoveries.saveAndFlush(recovery);
        assertThat(bootstrap.initialize(request(REPLACEMENT,true))).isEqualTo(Outcome.ROTATED);
        em.flush(); em.clear();
        var updated = users.findById(id).orElseThrow();
        assertThat(updated.getFirstName()).isEqualTo("Initial");
        assertThat(updated.getCredential().getEmail()).isEqualTo(address);
        assertThat(encoder.matches(REPLACEMENT,updated.getCredential().getPassword())).isTrue();
        assertThat(encoder.matches(ORIGINAL,updated.getCredential().getPassword())).isFalse();
        assertThat(recoveries.findById(recovery.getIdPasswordRecovery()).orElseThrow().isUsed()).isTrue();
        assertThat(recoveries.findById(recovery.getIdPasswordRecovery()).orElseThrow().getResetTokenHash()).isNull();
        mvc.perform(post("/api/auth/verify-password").header("Authorization","Bearer " + oldToken)
                .contentType("application/json").content(json.writeValueAsString(Map.of("password",REPLACEMENT))))
                .andExpect(status().isUnauthorized());
        login(REPLACEMENT);
        assertThat(bootstrap.initialize(request(REPLACEMENT,true))).isEqualTo(Outcome.UNCHANGED);
    }

    @Test void neverPromotesAnOrdinaryUser() {
        User ordinary = new User(); ordinary.setDocumentNumber(document); ordinary.setFirstName("Existing");
        ordinary.setLastName("User"); ordinary.setAccountStatus(AccountStatus.ACTIVE); users.saveAndFlush(ordinary);
        assertThatThrownBy(() -> bootstrap.initialize(request(ORIGINAL,false))).isInstanceOf(IllegalStateException.class);
        assertThat(assignments.findByUserId(ordinary.getIdUser())).isEmpty();
    }

    @Test void refusesAdditionalInitialAccountAndRotationOfAnUnknownAccount() {
        bootstrap.initialize(request(ORIGINAL,false));
        var other = new CoordinatorBootstrapRequestDTO("9876543210","Other","User","other@example.invalid",ORIGINAL,false);
        assertThatThrownBy(() -> bootstrap.initialize(other)).isInstanceOf(IllegalStateException.class);
        assertThat(users.findByDocumentNumber(other.document())).isEmpty();
        assertThatThrownBy(() -> bootstrap.initialize(new CoordinatorBootstrapRequestDTO(other.document(),null,null,other.email(),REPLACEMENT,true)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test void rejectsInvalidInputWithoutLeakingPasswords() {
        var invalid = new CoordinatorBootstrapRequestDTO(document,"Initial","Coordinator",address,"weak",false);
        assertThatThrownBy(() -> bootstrap.initialize(invalid)).isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining(invalid.password());
        assertThat(invalid.toString()).doesNotContain(invalid.password());
        assertThat(users.findByDocumentNumber(document)).isEmpty();
    }

    @Test void refusesWrongEmailAndInactiveAccountsWithoutReactivation() {
        bootstrap.initialize(request(ORIGINAL,false));
        assertThatThrownBy(() -> bootstrap.initialize(new CoordinatorBootstrapRequestDTO(document,null,null,"wrong@example.invalid",REPLACEMENT,true)))
                .isInstanceOf(IllegalStateException.class);
        var user = users.findByDocumentNumber(document).orElseThrow(); user.setAccountStatus(AccountStatus.INACTIVE);
        assertThatThrownBy(() -> bootstrap.initialize(request(REPLACEMENT,true))).isInstanceOf(IllegalStateException.class);
        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.INACTIVE);
    }

    private CoordinatorBootstrapRequestDTO request(String password, boolean rotate) {
        return new CoordinatorBootstrapRequestDTO(document,"Initial","Coordinator",address,password,rotate);
    }
    private String login(String password) throws Exception {
        var response = mvc.perform(post("/api/auth/login").contentType("application/json")
                .content(json.writeValueAsString(Map.of("numberDocument",document,"password",password,"privacyAccepted",true))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("COORDINATOR"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).path("token").asText();
    }
}
