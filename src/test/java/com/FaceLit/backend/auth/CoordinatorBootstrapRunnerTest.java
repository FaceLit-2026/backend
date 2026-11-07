package com.FaceLit.backend.auth;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.FaceLit.backend.auth.service.security.CoordinatorBootstrapService;
import com.FaceLit.backend.config.CoordinatorBootstrapConfiguration;
import com.FaceLit.backend.config.CoordinatorBootstrapProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;

@ExtendWith(OutputCaptureExtension.class)
class CoordinatorBootstrapRunnerTest {
    private final CoordinatorBootstrapService service = mock(CoordinatorBootstrapService.class);
    private final ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);

    @Test void normalStartupOnlyChecksForPublishedCredentials() throws Exception {
        runner(new CoordinatorBootstrapProperties());
        verify(service).verifyNoPublishedCredential(); verify(service,never()).initialize(any()); verifyNoInteractions(context);
    }
    @Test void oneTimeSetupClosesContextAndDoesNotLogSecrets(CapturedOutput output) throws Exception {
        var settings = new CoordinatorBootstrapProperties(); settings.setEnabled(true); settings.setExitAfter(true);
        settings.setPassword("PrivateTest1!"); settings.setDocument("1234567890"); settings.setEmail("test@example.invalid");
        when(service.initialize(any())).thenReturn(CoordinatorBootstrapService.Outcome.CREATED);
        runner(settings);
        verify(service).initialize(any()); verify(service).verifyNoPublishedCredential(); verify(context).close();
        assertThat(output).contains("CREATED").doesNotContain(settings.getPassword(),settings.getDocument(),settings.getEmail());
    }
    @Test void publishedCredentialBlocksStartupWithoutSilentRotation() {
        doThrow(new IllegalStateException("Rotate the retired credential")).when(service).verifyNoPublishedCredential();
        assertThatThrownBy(() -> runner(new CoordinatorBootstrapProperties())).isInstanceOf(IllegalStateException.class);
        verify(service,never()).initialize(any());
    }
    private void runner(CoordinatorBootstrapProperties settings) throws Exception {
        new CoordinatorBootstrapConfiguration().coordinatorBootstrap(settings,service,context).run(mock(ApplicationArguments.class));
    }
}
