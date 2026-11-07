package com.FaceLit.backend.config;

import com.FaceLit.backend.auth.dto.request.security.CoordinatorBootstrapRequestDTO;
import com.FaceLit.backend.auth.service.security.CoordinatorBootstrapService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CoordinatorBootstrapProperties.class)
public class CoordinatorBootstrapConfiguration {
    private static final Logger LOG = LoggerFactory.getLogger(CoordinatorBootstrapConfiguration.class);

    @Bean
    public ApplicationRunner coordinatorBootstrap(CoordinatorBootstrapProperties properties,
            CoordinatorBootstrapService service, ConfigurableApplicationContext context) {
        return arguments -> {
            if (properties.isEnabled()) {
                var input = new CoordinatorBootstrapRequestDTO(properties.getDocument(), properties.getFirstName(),
                        properties.getLastName(), properties.getEmail(), properties.getPassword(), properties.isRotatePassword());
                LOG.info("Coordinator bootstrap: {}", service.initialize(input));
            }
            service.verifyNoPublishedCredential();
            if (properties.isEnabled() && properties.isExitAfter()) context.close();
        };
    }
}
