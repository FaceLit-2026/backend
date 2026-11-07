package com.FaceLit.backend.shared.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mail.javamail.JavaMailSender;

class EmailServiceSenderTest {
    @ParameterizedTest
    @ValueSource(strings = {"recovery", "notification", "credentials"})
    void usesConfiguredSenderForEveryMailFlow(String flow) throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(message);
        EmailServiceImpl service = new EmailServiceImpl(sender, "sender@example.invalid");

        switch (flow) {
            case "recovery" -> service.sendRecoveryCode("recipient@example.invalid", "123456");
            case "notification" -> service.sendNotificationEmail("recipient@example.invalid", "Test notice", "Test body");
            case "credentials" -> service.sendInitialCredentials("recipient@example.invalid", "Test access", "<p>Test access</p>");
            default -> throw new IllegalArgumentException("Unknown mail flow");
        }

        assertThat(message.getFrom()).hasSize(1);
        assertThat(((InternetAddress) message.getFrom()[0]).getAddress()).isEqualTo("sender@example.invalid");
        assertThat(message.getRecipients(Message.RecipientType.TO)).hasSize(1);
        assertThat(((InternetAddress) message.getRecipients(Message.RecipientType.TO)[0]).getAddress())
                .isEqualTo("recipient@example.invalid");
        verify(sender).send(message);
    }
}
