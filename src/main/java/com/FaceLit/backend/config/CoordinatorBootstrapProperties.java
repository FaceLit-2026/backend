package com.FaceLit.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "facelit.bootstrap.coordinator")
public class CoordinatorBootstrapProperties {
    private boolean enabled;
    private boolean rotatePassword;
    private boolean exitAfter;
    private String document;
    private String firstName;
    private String lastName;
    private String email;
    private String password;
}
