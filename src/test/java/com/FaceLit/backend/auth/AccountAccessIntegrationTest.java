package com.FaceLit.backend.auth;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import com.FaceLit.backend.auth.dto.request.security.CompleteAccountAccessDTO;
import com.FaceLit.backend.auth.dto.request.security.RequestPasswordRecoveryDTO;
import com.FaceLit.backend.auth.dto.request.security.ResetPasswordDTO;
import com.FaceLit.backend.auth.model.enums.*;
import com.FaceLit.backend.auth.model.security.*;
import com.FaceLit.backend.auth.repository.security.*;
import com.FaceLit.backend.auth.service.serviceImpl.security.AccountAccessService;
import com.FaceLit.backend.auth.service.serviceImpl.security.PasswordRecoveryServiceImpl;
import com.FaceLit.backend.shared.service.EmailService;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AccountAccessIntegrationTest {
    @MockitoBean EmailService email;
    @Autowired AccountAccessService access;
    @Autowired UserRepository users;
    @Autowired CredentialRepository credentials;
    @Autowired AccountAccessRequestRepository requests;
    @Autowired PasswordRecoveryRepository recoveries;
    @Autowired PasswordEncoder encoder;
    @Autowired PasswordRecoveryServiceImpl recoveryService;
    @Autowired com.FaceLit.backend.auth.repository.roleandpermission.RoleRepository roles;
    @Autowired com.FaceLit.backend.auth.service.serviceImpl.security.UserManagementServiceImpl management;
    @Autowired com.FaceLit.backend.academic.service.serviceImpl.academic.InstructorServiceImpl instructors;
    @Autowired com.FaceLit.backend.academic.service.serviceImpl.academic.UserChipServiceImpl apprentices;
    @Autowired com.FaceLit.backend.academic.service.serviceImpl.academic.CsvAcademicServiceImpl csv;
    @Autowired com.FaceLit.backend.academic.repository.ProgramRepository programs;
    @Autowired com.FaceLit.backend.academic.repository.ChipRepository chips;
    @Autowired com.FaceLit.backend.notification.repository.NotificationRepository notices;
    @Autowired com.FaceLit.backend.notification.service.NotificationService notificationService;
    @Autowired com.FaceLit.backend.notification.service.serviceImpl.EmailDispatchServiceImpl dispatcher;
    @Autowired jakarta.persistence.EntityManager entityManager;
    User user;
    Credential credential;

    @BeforeEach void createAccount() {
        for (RoleName name : new RoleName[]{RoleName.APPRENTICE, RoleName.INSTRUCTOR, RoleName.COORDINATOR}) {
            if (roles.findByNameRole(name).isEmpty()) {
                var role = new com.FaceLit.backend.auth.model.roleandpermission.Role(); role.setNameRole(name); roles.saveAndFlush(role);
            }
        }
        user = new User(); user.setDocumentNumber(String.format("%010d", Math.abs(UUID.randomUUID().getMostSignificantBits() % 10000000000L)));
        user.setFirstName("Maria"); user.setLastName("Prueba"); user.setAccountStatus(AccountStatus.ACTIVE);
        users.saveAndFlush(user);
        credential = new Credential(); credential.setUser(user); credential.setEmail(UUID.randomUUID() + "@example.com");
        credential.setCredentialStatus(CredentialStatus.ACTIVE); credential.setPassword(encoder.encode("Anterior1!"));
        credentials.saveAndFlush(credential); user.setCredential(credential);
    }

    AccountAccessRequest pending() { return requests.findByUser_IdUserAndUsedFalse(user.getIdUser()).get(0); }
    String deliverNotice(AccountAccessRequest request) {
        clearInvocations(email); access.dispatch(request.getId());
        var body = ArgumentCaptor.forClass(String.class);
        verify(email).sendInitialCredentials(eq(request.getTargetEmail()), anyString(), body.capture());
        assertThat(body.getValue()).doesNotContain("Anterior1!", "accessToken=", "/auth/new-password");
        return body.getValue();
    }
    String deliver(AccountAccessRequest request) {
        var matcher = Pattern.compile("data-credential-password=\"true\"[^>]*>([^<]+)<").matcher(deliverNotice(request));
        assertThat(matcher.find()).isTrue();
        return matcher.group(1).replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"");
    }
    String codeFor(String address) {
        clearInvocations(email);
        var dto = new RequestPasswordRecoveryDTO(); dto.setEmail(address); recoveryService.requestRecovery(dto);
        var code = ArgumentCaptor.forClass(String.class); verify(email).sendRecoveryCode(eq(address.toLowerCase(java.util.Locale.ROOT)), code.capture());
        assertThat(code.getValue()).matches("[0-9]{6}"); addresses.put(code.getValue(), address); return code.getValue();
    }
    final java.util.Map<String, String> addresses = new java.util.HashMap<>();
    final java.util.Map<String, String> authorizations = new java.util.HashMap<>();
    void verifyCode(String code) {
        var dto = new com.FaceLit.backend.auth.dto.request.security.VerifyTokenDTO(); dto.setToken(code); dto.setEmail(addresses.get(code)); authorizations.put(code, recoveryService.verifyToken(dto).getResetToken());
    }
    void resetCode(String code) {
        var dto = new ResetPasswordDTO(); dto.setEmail(addresses.get(code)); dto.setToken(authorizations.getOrDefault(code, code)); dto.setNewPassword("NuevaClave1!"); dto.setConfirmPassword("NuevaClave1!"); recoveryService.resetPassword(dto);
    }
    void finishEmailChange(String address) {
        String code = codeFor(address); verifyCode(code); resetCode(code);
    }
    CompleteAccountAccessDTO completion(String token) {
        var dto = new CompleteAccountAccessDTO(); dto.setToken(token); dto.setNewPassword("NuevaClave1!"); dto.setConfirmPassword("NuevaClave1!"); return dto;
    }

    @Test void welcomeDeliversGeneratedPasswordWhoseHashAuthenticatesSameAccount() {
        UUID id = user.getIdUser(); String oldHash = credential.getPassword();
        access.welcome(user, credential); var request = pending(); String password = deliver(request);
        assertThat(password).hasSize(12); assertThat(encoder.matches(password, credential.getPassword())).isTrue();
        assertThat(credential.getPassword()).isNotEqualTo(oldHash).isNotEqualTo(password);
        assertThat(request.getTokenHash()).isNull(); assertThat(request.isUsed()).isTrue();
        assertThat(request.getSendStatus()).isEqualTo("SENT"); assertThat(user.getIdUser()).isEqualTo(id);
        clearInvocations(email); access.dispatch(request.getId()); verifyNoInteractions(email);
    }

    @Test void firstDeliveryUsesThePasswordAlreadyGeneratedAtRegistration() {
        String raw = "Inicial123!X"; credential.setPassword(encoder.encode(raw)); credentials.flush();
        access.welcome(user, credential, raw); var request = pending();
        access.dispatch(request.getId(), raw);
        verify(email).sendInitialCredentials(eq(credential.getEmail()), anyString(), contains(raw));
        assertThat(encoder.matches(raw, credential.getPassword())).isTrue();
        assertThat(new com.FaceLit.backend.auth.service.security.InitialCredentialsEmailRequested(request.getId(), raw).toString()).doesNotContain(raw);
    }

    @Test void failedWelcomeIsDurableAndCsvRetryDoesNotDuplicateAccount() {
        String hash = credential.getPassword(); access.welcome(user, credential); var request = pending();
        doThrow(new IllegalStateException("SMTP secret must not be stored")).when(email).sendInitialCredentials(anyString(), anyString(), anyString());
        access.dispatch(request.getId());
        assertThat(request.getSendStatus()).isEqualTo("FAILED"); assertThat(request.getAttempts()).isEqualTo(1);
        assertThat(request.getLastError()).isEqualTo("IllegalStateException");
        assertThat(credential.getPassword()).isEqualTo(hash);
        request.setLastAttemptAt(OffsetDateTime.now().minusMinutes(2)); access.retryWelcome(user);
        assertThat(request.getSendStatus()).isEqualTo("PENDING");
        assertThat(requests.findByUser_IdUserAndUsedFalse(user.getIdUser())).hasSize(1);
        reset(email); String password = deliver(request);
        assertThat(encoder.matches(password, credential.getPassword())).isTrue();
        assertThat(users.findById(user.getIdUser())).isPresent();
    }

    @Test void unchangedCsvDoesNotRepeatSuccessfulWelcomeOrPassword() {
        access.welcome(user, credential); var request = pending(); deliver(request);
        String hash = credential.getPassword(); clearInvocations(email);
        access.retryWelcome(user); access.dispatch(request.getId());
        verifyNoInteractions(email); assertThat(credential.getPassword()).isEqualTo(hash);
        assertThat(requests.findByUser_IdUserAndUsedFalse(user.getIdUser())).isEmpty();
    }

    @Test void emailChangeImmediatelyUpdatesRecoveryAndSendsNewPasswordInSameTemplate() {
        String oldEmail = credential.getEmail(); String oldHash = credential.getPassword(); UUID id = user.getIdUser();
        String oldCode = codeFor(oldEmail);
        access.changeEmail(user, credential, "NUEVO@example.com");
        assertThat(credential.getEmail()).isEqualTo("nuevo@example.com");
        assertThat(recoveries.findByToken(oldCode)).get().extracting(PasswordRecovery::isUsed).isEqualTo(true);
        assertThat(credentials.findByEmailIgnoreCase(oldEmail)).isEmpty();
        String password = deliver(pending());
        assertThat(encoder.matches(password, credential.getPassword())).isTrue();
        assertThat(credential.getPassword()).isNotEqualTo(oldHash);
        assertThat(user.getIdUser()).isEqualTo(id);
        var dto = new RequestPasswordRecoveryDTO(); dto.setEmail(oldEmail);
        assertThatThrownBy(() -> recoveryService.requestRecovery(dto)).hasMessageContaining("Correo no registrado");
        String code = codeFor("nuevo@example.com"); verifyCode(code); resetCode(code);
        assertThat(encoder.matches("NuevaClave1!", credential.getPassword())).isTrue();
    }

    @Test void recoveryResendInvalidatesEarlierCodeAndExpiredAndUsedCodesAreRejected() {
        access.changeEmail(user, credential, "current@example.com"); deliver(pending());
        String first = codeFor("current@example.com"); String next = codeFor("current@example.com");
        assertThat(recoveries.findByToken(first)).get().extracting(PasswordRecovery::isUsed).isEqualTo(true);
        assertThatThrownBy(() -> verifyCode(first)).hasMessageContaining("incorrecto");
        verifyCode(next); resetCode(next);
        assertThatThrownBy(() -> resetCode(next)).hasMessageContaining("inválida");
        String expired = codeFor("current@example.com");
        recoveries.findByToken(expired).orElseThrow().setExpirationDate(OffsetDateTime.now().minusSeconds(1)); recoveries.flush();
        String hash = credential.getPassword();
        assertThatThrownBy(() -> verifyCode(expired)).hasMessageContaining("vencido");
        assertThat(credential.getPassword()).isEqualTo(hash);
    }

    @Test void newCorrectionCancelsStaleDeliveryAndPreservesHistory() {
        access.changeEmail(user, credential, "first@example.com"); var first = pending();
        String code = codeFor("first@example.com");
        access.changeEmail(user, credential, "second@example.com"); var second = pending();
        assertThat(first.isUsed()).isTrue(); assertThat(first.getSendStatus()).isEqualTo("CANCELLED");
        clearInvocations(email); access.dispatch(first.getId()); verifyNoInteractions(email);
        assertThatThrownBy(() -> resetCode(code)).hasMessageContaining("inválida");
        deliver(second); assertThat(credential.getEmail()).isEqualTo("second@example.com");
        assertThat(requests.findById(first.getId())).isPresent();
    }

    @Test void duplicateEmailIsRejectedWithoutChangingCredentialsOrQueueingDelivery() {
        var other = new User(); other.setDocumentNumber("9988776655"); other.setFirstName("Otro"); other.setLastName("Usuario"); other.setAccountStatus(AccountStatus.ACTIVE); users.saveAndFlush(other);
        var otherCredential = new Credential(); otherCredential.setUser(other); otherCredential.setEmail("occupied@example.com"); otherCredential.setPassword(encoder.encode("Otra123!")); otherCredential.setCredentialStatus(CredentialStatus.ACTIVE); credentials.saveAndFlush(otherCredential);
        String original = credential.getEmail(); String hash = credential.getPassword();
        assertThatThrownBy(() -> access.changeEmail(user, credential, "occupied@example.com")).hasMessageContaining("Ya existe");
        assertThat(credential.getEmail()).isEqualTo(original); assertThat(credential.getPassword()).isEqualTo(hash);
        assertThat(requests.findByUser_IdUserAndUsedFalse(user.getIdUser())).isEmpty();
    }

    @Test void failedEmailChangeKeepsPasswordAndRecoveryWorksAtNewAddress() {
        String oldEmail = credential.getEmail(); String hash = credential.getPassword();
        access.changeEmail(user, credential, "failed@example.com"); var request = pending();
        doThrow(new IllegalStateException()).when(email).sendInitialCredentials(anyString(), anyString(), anyString());
        access.dispatch(request.getId());
        assertThat(credential.getEmail()).isEqualTo("failed@example.com");
        assertThat(credential.getPassword()).isEqualTo(hash);
        assertThat(request.getSendStatus()).isEqualTo("FAILED");
        reset(email); String code = codeFor("failed@example.com"); verifyCode(code); resetCode(code);
        clearInvocations(email); access.dispatch(request.getId()); verifyNoInteractions(email);
        assertThat(request.getSendStatus()).isEqualTo("CANCELLED");
        assertThat(encoder.matches("NuevaClave1!", credential.getPassword())).isTrue();
        assertThat(credentials.findByEmailIgnoreCase(oldEmail)).isEmpty();
    }

    @Test void deliveryStopsAfterThreeFailuresAndCsvCanRetryEmailChangeWithoutDuplicatingIt() {
        access.changeEmail(user, credential, "retry@example.com"); var request = pending();
        doThrow(new IllegalStateException()).when(email).sendInitialCredentials(anyString(), anyString(), anyString());
        for (int i = 0; i < 5; i++) access.dispatch(request.getId());
        assertThat(request.getAttempts()).isEqualTo(3); verify(email, times(3)).sendInitialCredentials(anyString(), anyString(), anyString());
        request.setLastAttemptAt(OffsetDateTime.now().minusMinutes(2)); access.retryWelcome(user);
        reset(email); deliver(request); assertThat(request.getSendStatus()).isEqualTo("SENT");
    }

    @Test void sameEmailCaseAndWhitespaceDoNotResetPasswordOrSendCredentials() {
        String hash = credential.getPassword(); access.changeEmail(user, credential, " " + credential.getEmail().toUpperCase(java.util.Locale.ROOT) + " ");
        assertThat(requests.findByUser_IdUserAndUsedFalse(user.getIdUser())).isEmpty();
        assertThat(credential.getPassword()).isEqualTo(hash);
    }

    com.FaceLit.backend.academic.model.academic.Chip createChip() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        var program = new com.FaceLit.backend.academic.model.academic.Program();
        program.setProgramCode("T" + suffix); program.setProgramName("Programa " + suffix);
        program.setState(com.FaceLit.backend.academic.model.enums.AcademicState.ACTIVE); programs.saveAndFlush(program);
        var chip = new com.FaceLit.backend.academic.model.academic.Chip(); chip.setChipCode("9" + Math.abs(UUID.randomUUID().getMostSignificantBits() % 100000000L));
        chip.setProgram(program); chip.setState(com.FaceLit.backend.academic.model.enums.AcademicState.ACTIVE);
        return chips.saveAndFlush(chip);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = RoleName.class, names = {"APPRENTICE", "INSTRUCTOR", "COORDINATOR"})
    void manualRegistrationAndEmailChangeDeliverCredentialsForAllThreeRoles(RoleName role) {
        String document = "8" + Math.abs(UUID.randomUUID().getMostSignificantBits() % 100000000L);
        String address = document + "@example.com";
        UUID instructorId = null;
        if (role == RoleName.COORDINATOR) {
            var dto = new com.FaceLit.backend.auth.dto.request.security.CreateManagedUserRequestDTO();
            dto.setNumberDocument(document); dto.setFirstName("Maria"); dto.setLastName("Prueba"); dto.setEmail(address); dto.setRole(role);
            management.createUser(dto);
        } else if (role == RoleName.INSTRUCTOR) {
            var dto = new com.FaceLit.backend.academic.dto.request.academic.InstructorRequestDTO();
            dto.setDocumento(document); dto.setNombre("Maria"); dto.setApellido("Prueba"); dto.setCorreo(address);
            dto.setInstructorType(com.FaceLit.backend.academic.model.enums.InstructorType.TRANSVERSAL);
            instructorId = instructors.create(dto).getIdInstructor();
        } else {
            var dto = new com.FaceLit.backend.academic.dto.request.academic.UserChipRequestDTO();
            dto.setDocumento(document); dto.setNombre("Maria"); dto.setApellido("Prueba"); dto.setCorreo(address);
            apprentices.assignInitialChip(createChip().getIdChip(), dto);
        }
        user = users.findByDocumentNumber(document).orElseThrow(); credential = credentials.findByUser(user).orElseThrow();
        assertThat(pending().getPurpose()).isEqualTo("WELCOME");
        var welcomeRequest = pending(); String welcomePassword = deliver(welcomeRequest);
        assertThat(encoder.matches(welcomePassword, credential.getPassword())).isTrue();
        verify(email).sendInitialCredentials(eq(address), anyString(), contains(com.FaceLit.backend.notification.service.ProfileChangeNotificationService.roleLabel(role.name())));
        // A subsequent HTTP update runs in a new persistence context. Reload the inverse one-to-one relation accordingly.
        UUID accountId = user.getIdUser(); entityManager.flush(); entityManager.clear();
        user = users.findById(accountId).orElseThrow(); credential = credentials.findByUser(user).orElseThrow();
        String hash = credential.getPassword();
        if (role == RoleName.COORDINATOR) {
            var dto = new com.FaceLit.backend.auth.dto.request.security.UpdateUserRequestDTO();
            dto.setNumberDocument(document); dto.setFirstName("Maria Jose"); dto.setLastName("Prueba"); dto.setEmail("new" + address);
            dto.setRole(role); dto.setAccountStatus(AccountStatus.ACTIVE); management.updateUser(user.getIdUser(), dto);
        } else if (role == RoleName.INSTRUCTOR) {
            var dto = new com.FaceLit.backend.academic.dto.request.academic.InstructorRequestDTO();
            dto.setDocumento(document); dto.setNombre("Maria Jose"); dto.setApellido("Prueba"); dto.setCorreo("new" + address);
            dto.setInstructorType(com.FaceLit.backend.academic.model.enums.InstructorType.TRANSVERSAL); instructors.update(instructorId, dto);
        } else {
            var dto = new com.FaceLit.backend.academic.dto.request.academic.UserChipRequestDTO();
            dto.setDocumento(document); dto.setNombre("Maria Jose"); dto.setApellido("Prueba"); dto.setCorreo("new" + address);
            apprentices.updateApprentice(user.getIdUser(), dto);
        }
        assertThat(credential.getEmail()).isEqualTo("new" + address); assertThat(credential.getPassword()).isEqualTo(hash);
        assertThat(notices.findByIdUserAppOrderByCreatedAtDesc(user.getIdUser()))
                .anySatisfy(n -> assertThat(n.getMetadataJson()).contains("profileChanges", "nombre", "Maria Jose"));
        assertThat(pending().getPurpose()).isEqualTo("EMAIL_CHANGE");
        String newPassword = deliver(pending());
        assertThat(newPassword).isNotEqualTo(welcomePassword);
        assertThat(encoder.matches(newPassword, credential.getPassword())).isTrue();
        assertThat(credential.getEmail()).isEqualTo("new" + address);
        assertThat(welcomeRequest.isUsed()).isTrue();
        long count = requests.count(); access.changeEmail(user, credential, "new" + address);
        assertThat(requests.count()).isEqualTo(count);

    }

    byte[] sha256(String value) {
        try { return java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }

    @Test void csvCreatesBothRolesAndReimportOnlyQueuesActualEmailChange() {
        var chip = createChip(); String docA = "71" + Math.abs(UUID.randomUUID().getMostSignificantBits() % 10000000L);
        String docI = "72" + Math.abs(UUID.randomUUID().getMostSignificantBits() % 10000000L);
        String content = "tipo,documento,nombre,apellido,correo,programa_codigo,ficha_codigo,instructor_tipo\n"
                + "aprendiz," + docA + ",Ana,Prueba," + docA + "@example.com,," + chip.getChipCode() + ",\n"
                + "instructor," + docI + ",Luis,Prueba," + docI + "@example.com,,,transversal\n";
        var result = csv.upload(file(content)); assertThat(result.getErroresDeReferencia()).isEmpty();
        User learner = users.findByDocumentNumber(docA).orElseThrow(); User instructor = users.findByDocumentNumber(docI).orElseThrow();
        for (User account : new User[]{learner, instructor}) {
            user = account; credential = credentials.findByUser(account).orElseThrow(); deliver(pending());
        }
        long requestCount = requests.count();
        var learnerCredential = credentials.findByUser(learner).orElseThrow(); String hash = learnerCredential.getPassword();
        csv.upload(file(content));
        assertThat(requests.count()).isEqualTo(requestCount);
        // CSV summaries can be emitted, but there must be no duplicate personal update notices.
        assertThat(notices.findByIdUserAppOrderByCreatedAtDesc(learner.getIdUser())).noneMatch(n -> n.getType().equals("apprentice_profile_updated"));
        assertThat(learnerCredential.getPassword()).isEqualTo(hash);
        String updatedContent = content.replace(docA + "@example.com", "changed" + docA + "@example.com")
                .replace(docI + "@example.com", "changed" + docI + "@example.com");
        csv.upload(file(updatedContent));
        user = learner; credential = learnerCredential;
        assertThat(pending().getPurpose()).isEqualTo("EMAIL_CHANGE");
        assertThat(credential.getEmail()).isEqualTo("changed" + docA + "@example.com");
        String generated = deliver(pending());
        assertThat(encoder.matches(generated, credential.getPassword())).isTrue();
        assertThat(credential.getEmail()).isEqualTo("changed" + docA + "@example.com");
        assertThat(users.findByDocumentNumber(docA)).get().extracting(User::getIdUser).isEqualTo(learner.getIdUser());
        user = instructor; credential = credentials.findByUser(instructor).orElseThrow();
        assertThat(credential.getEmail()).isEqualTo("changed" + docI + "@example.com");
        String instructorPassword = deliver(pending());
        assertThat(encoder.matches(instructorPassword, credential.getPassword())).isTrue();
        long afterUpdate = requests.count(); String instructorHash = credential.getPassword();
        csv.upload(file(updatedContent));
        assertThat(requests.count()).isEqualTo(afterUpdate);
        assertThat(credential.getPassword()).isEqualTo(instructorHash);
    }

    org.springframework.mock.web.MockMultipartFile file(String text) {
        return new org.springframework.mock.web.MockMultipartFile("file", "access.csv", "text/csv", text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test void instructorNameAndTypeChangesShareOneDetailedProfileNotice() {
        var chip = createChip();
        var dto = new com.FaceLit.backend.academic.dto.request.academic.InstructorRequestDTO();
        dto.setDocumento("83" + Math.abs(UUID.randomUUID().getMostSignificantBits() % 10000000L));
        dto.setNombre("Luis"); dto.setApellido("Prueba"); dto.setCorreo(dto.getDocumento() + "@example.com");
        dto.setInstructorType(com.FaceLit.backend.academic.model.enums.InstructorType.ESPECIFICO);
        dto.setProgramIds(java.util.List.of(chip.getProgram().getIdProgram()));
        var created = instructors.create(dto); entityManager.flush(); entityManager.clear();
        dto.setNombre("Luis Alberto"); dto.setInstructorType(com.FaceLit.backend.academic.model.enums.InstructorType.TRANSVERSAL);
        dto.setProgramIds(java.util.List.of()); instructors.update(created.getIdInstructor(), dto);
        var profiles = notices.findByIdUserAppOrderByCreatedAtDesc(created.getIdUser()).stream()
                .filter(n -> n.getType().equals("instructor_profile_updated")).toList();
        assertThat(profiles).hasSize(1);
        assertThat(profiles.get(0).getMetadataJson()).contains("tipoInstructor", "nombre", "ESPECIFICO", "TRANSVERSAL", "Luis Alberto");
        assertThat(profiles.get(0).getMessage()).contains("Específico", "Transversal");
    }

    @Test void personalDataUpdatePreservesCredentialsAndQueuesEmailOnlyWhenChanged() {
        String hash = credential.getPassword();
        var dto = new com.FaceLit.backend.auth.dto.request.security.UpdateUserRequestDTO();
        dto.setNumberDocument(user.getDocumentNumber()); dto.setFirstName("Maria Jose"); dto.setLastName(user.getLastName());
        dto.setEmail(credential.getEmail()); dto.setRole(RoleName.COORDINATOR); dto.setAccountStatus(AccountStatus.ACTIVE);
        management.updateUser(user.getIdUser(), dto);
        assertThat(credential.getPassword()).isEqualTo(hash);
        assertThat(requests.findByUser_IdUserAndUsedFalse(user.getIdUser())).isEmpty();
        var profileNotices = notices.findByIdUserAppOrderByCreatedAtDesc(user.getIdUser()).stream()
                .filter(n -> n.getType().equals("user_profile_updated")).toList();
        assertThat(profileNotices).hasSize(1); assertThat(profileNotices.get(0).getChannel()).isEqualTo("APP_EMAIL");
        management.updateUser(user.getIdUser(), dto);
        assertThat(notices.findByIdUserAppOrderByCreatedAtDesc(user.getIdUser()).stream().filter(n -> n.getType().equals("user_profile_updated"))).hasSize(1);
    }

    @Test void futureNotificationsUseUpdatedEmailAndHistoricalNoticesStayWithSameUser() {
        UUID id = user.getIdUser(); access.changeEmail(user, credential, "verified@example.com");
        notificationService.createForRecipient(new com.FaceLit.backend.notification.dto.request.CreateNotificationRequestDTO(id, "user_profile_updated", "Historico", "Datos previos", id, "user", null, null));
        var historical = notices.findByIdUserAppOrderByCreatedAtDesc(id).get(0).getIdNotification();
        deliver(pending()); finishEmailChange("verified@example.com");
        var notice = notices.findByIdUserAppOrderByCreatedAtDesc(id).stream().filter(n -> n.getType().equals("password_changed")).findFirst().orElseThrow();
        clearInvocations(email);
        // Exercise the actual synchronous dispatch inside this rolled-back test transaction;
        // the asynchronous AFTER_COMMIT listener intentionally cannot see uncommitted test rows.
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(dispatcher, "sendAndLog", notice.getIdNotification());
        verify(email).sendNotificationEmail(eq("verified@example.com"), anyString(), anyString());
        assertThat(notices.findById(historical)).get().extracting(n -> n.getIdUserApp()).isEqualTo(id);
    }
}
