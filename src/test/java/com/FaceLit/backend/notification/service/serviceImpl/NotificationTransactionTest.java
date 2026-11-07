package com.FaceLit.backend.notification.service.serviceImpl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import com.FaceLit.backend.auth.repository.roleandpermission.UserRoleRepository;
import com.FaceLit.backend.notification.repository.*;
import com.FaceLit.backend.notification.model.Notification;
import com.FaceLit.backend.notification.service.*;
import com.FaceLit.backend.notification.dto.request.MonolithEventRequestDTO;
import com.FaceLit.backend.shared.service.UserDisplayNameService;

class NotificationTransactionTest {
    @Test void failedLoginCommitsCounterAndSecurityNoticeDespiteAuthenticationError() {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:login-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(ds); jdbc.execute("create table attempts (attempt_count int)"); jdbc.update("insert into attempts values (1)");
        jdbc.execute("create table security_notice (id int)");
        var manager = new DataSourceTransactionManager(ds);
        var credentials = mock(com.FaceLit.backend.auth.repository.security.CredentialRepository.class);
        var notices = mock(NotificationService.class);
        var encoder = mock(org.springframework.security.crypto.password.PasswordEncoder.class);
        var user = new com.FaceLit.backend.auth.model.security.User(); user.setIdUser(UUID.randomUUID()); user.setAccountStatus(com.FaceLit.backend.auth.model.enums.AccountStatus.ACTIVE);
        var credential = new com.FaceLit.backend.auth.model.security.Credential(); credential.setUser(user); credential.setPassword("hash"); credential.setFailedAttempts(1);
        when(credentials.findByUser_DocumentNumber("123")).thenReturn(Optional.of(credential));
        when(credentials.lockByUserId(user.getIdUser())).thenReturn(Optional.of(credential));
        when(credentials.save(any())).thenAnswer(call -> { jdbc.update("update attempts set attempt_count = ?", credential.getFailedAttempts()); return credential; });
        when(notices.createCoordinatorEvent(any())).thenAnswer(call -> { jdbc.update("insert into security_notice values (1)"); return List.of(); });
        var target = new com.FaceLit.backend.auth.service.serviceImpl.roleandpermission.LoginServiceImpl(credentials, mock(UserRoleRepository.class),
                mock(com.FaceLit.backend.auth.repository.roleandpermission.RolePermissionRepository.class), encoder,
                mock(com.FaceLit.backend.auth.service.roleandpermission.JwtService.class), notices,
                mock(com.FaceLit.backend.auth.repository.legal.AcceptanceTermsRepository.class), mock(com.FaceLit.backend.auth.repository.security.UserSessionRepository.class));
        org.springframework.test.util.ReflectionTestUtils.setField(target, "entityManager", mock(jakarta.persistence.EntityManager.class));
        var factory = new ProxyFactory(target); factory.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        var service = (com.FaceLit.backend.auth.service.roleandpermission.LoginService) factory.getProxy();
        var dto = new com.FaceLit.backend.auth.dto.request.roleandpermission.LoginRequestDTO(); dto.setEmail("123"); dto.setPassword("incorrect");
        assertThatThrownBy(() -> service.login(dto)).isInstanceOf(com.FaceLit.backend.auth.exception.LoginException.class);
        assertThat(jdbc.queryForObject("select attempt_count from attempts", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from security_notice", Integer.class)).isEqualTo(1);
        credential.setFailedAttempts(com.FaceLit.backend.shared.constants.AppConstants.MAX_LOGIN_ATTEMPTS - 1);
        assertThatThrownBy(() -> service.login(dto)).isInstanceOf(com.FaceLit.backend.auth.exception.LoginException.class);
        assertThat(credential.getLockedUntil()).isAfter(java.time.OffsetDateTime.now());
        assertThat(jdbc.queryForObject("select attempt_count from attempts", Integer.class)).isEqualTo(com.FaceLit.backend.shared.constants.AppConstants.MAX_LOGIN_ATTEMPTS);
        verify(notices).createCoordinatorEvent(argThat(n -> n.type().equals("security_account_locked")));
        assertThatThrownBy(() -> service.login(dto)).hasMessageContaining("bloqueada");
        assertThat(jdbc.queryForObject("select count(*) from security_notice", Integer.class)).isEqualTo(2);
    }
    @Test void blockedDeleteNoticeCommitsEvenWhenBusinessTransactionRollsBack() {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:notice-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(ds);
        jdbc.execute("create table notice (id varchar primary key)");
        jdbc.execute("create table business_change (id int primary key)");
        var manager = new DataSourceTransactionManager(ds);
        var repo = mock(NotificationRepository.class);
        when(repo.save(any())).thenAnswer(call -> {
            Notification n = call.getArgument(0); n.setIdNotification(UUID.randomUUID());
            jdbc.update("insert into notice values (?)", n.getIdNotification().toString()); return n;
        });
        var target = new NotificationServiceImpl(repo, mock(EmailLogRepository.class), mock(UserRoleRepository.class),
                new NotificationCatalog(), mock(org.springframework.context.ApplicationEventPublisher.class), mock(UserDisplayNameService.class));
        var factory = new ProxyFactory(target);
        factory.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        NotificationService service = (NotificationService) factory.getProxy();
        assertThatThrownBy(() -> new TransactionTemplate(manager).executeWithoutResult(tx -> {
            jdbc.update("insert into business_change values (1)");
            service.createIndependentCoordinatorEvent(new MonolithEventRequestDTO("academic_delete_blocked", "Bloqueado", "Dependencias", UUID.randomUUID(), UUID.randomUUID(), "chip", null, "{}"));
            throw new IllegalStateException("blocked");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("select count(*) from notice", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from business_change", Integer.class)).isZero();
    }
}
