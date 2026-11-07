package com.FaceLit.backend.auth;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.FaceLit.backend.academic.model.academic.*;
import com.FaceLit.backend.academic.model.enums.AcademicState;
import com.FaceLit.backend.academic.repository.*;
import com.FaceLit.backend.auth.model.security.User;
import com.FaceLit.backend.auth.service.serviceImpl.security.InitialCredentialEmailTemplate;
import com.FaceLit.backend.shared.service.impl.EmailServiceImpl;
import jakarta.mail.*;
import jakarta.mail.internet.MimeMessage;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

class InitialCredentialEmailTemplateTest {
    UserChipRepository users = mock(UserChipRepository.class);
    InstructorRepository instructors = mock(InstructorRepository.class);
    InstructorProgramRepository programs = mock(InstructorProgramRepository.class);
    InstructorChipRepository chips = mock(InstructorChipRepository.class);
    InitialCredentialEmailTemplate template = new InitialCredentialEmailTemplate(users, instructors, programs, chips);

    User user() {
        User user = new User(); user.setIdUser(UUID.randomUUID()); user.setFirstName("ANA MARÍA"); user.setLastName("EJEMPLO"); user.setDocumentNumber("1000000000"); return user;
    }
    String password = "A1!DemoClave";
    String recovery = "https://facelit.example/auth/password-recovery?source=email&lang=es";
    String login = "https://facelit.example/auth/login";

    @Test void apprenticeGetsOriginalPresentationAndAcademicCardsAndCreatesReviewablePreview() throws Exception {
        User user = user(); var program = new Program(); program.setProgramName("Análisis y Desarrollo de Software");
        var chip = new Chip(); chip.setChipCode("3145555"); chip.setProgram(program); var assignment = new UserChip(); assignment.setChip(chip);
        when(users.findByApprentice_User_IdUserAndState(user.getIdUser(), AcademicState.ACTIVE)).thenReturn(Optional.of(assignment));
        String html = template.render(user, "Aprendiz", "aprendiz@example.com", password, login, recovery, false);
        assertThat(html).contains("Credenciales de acceso", "cid:facelitLogo", "ANA MARÍA EJEMPLO", "Aprendiz", "1000000000",
                "3145555", "Análisis y Desarrollo de Software", password, "Ingresar a FaceLit", "seis dígitos", "aprendiz@example.com");
        assertThat(html).doesNotContain("APPRENTICE", "Detalle de la notificación", "accessToken=", "Establecer mi contraseña", "24 horas");
        String logo = Base64.getEncoder().encodeToString(Files.readAllBytes(Path.of("src/main/resources/static/email/facelit-logo.png")));
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/welcome-email-preview.html"), html.replace("cid:facelitLogo", "data:image/png;base64," + logo), StandardCharsets.UTF_8);
    }

    @Test void coordinatorDoesNotGetEmptyAcademicCards() {
        String html = template.render(user(), "Coordinador", "aprendiz@example.com", password, login, recovery, false);
        assertThat(html).contains("Coordinador").doesNotContain(">Ficha<", ">Programa<", "null");
    }

    @Test void updatedEmailUsesSamePresentationWithNewAddressPasswordAndOptionalRecovery() throws Exception {
        String html = template.render(user(), "Coordinador", "nuevo@example.com", "B2!NuevaClave", login, recovery, true);
        assertThat(html).contains("Credenciales de acceso", "cid:facelitLogo", "nuevo@example.com", "B2!NuevaClave",
                "fue actualizado", "reemplaza la anterior", "cuando quieras", "/auth/password-recovery", "Ingresar a FaceLit");
        assertThat(html).doesNotContain("fue creada", "accessToken=", "24 horas", "Establecer mi contraseña");
        String logo = Base64.getEncoder().encodeToString(Files.readAllBytes(Path.of("src/main/resources/static/email/facelit-logo.png")));
        Files.writeString(Path.of("target/updated-email-preview.html"), html.replace("cid:facelitLogo", "data:image/png;base64," + logo), StandardCharsets.UTF_8);
    }

    @Test void listenerPassesOriginalGeneratedPasswordToCommittedDelivery() {
        var access = mock(com.FaceLit.backend.auth.service.serviceImpl.security.AccountAccessService.class);
        var event = new com.FaceLit.backend.auth.service.security.InitialCredentialsEmailRequested(UUID.randomUUID(), "A1!OriginalClave");
        new com.FaceLit.backend.auth.service.serviceImpl.security.InitialCredentialEmailListener(access).onInitialCredentialsEmailRequested(event);
        verify(access).dispatch(event.requestId(), "A1!OriginalClave");
        assertThat(event.toString()).doesNotContain("A1!OriginalClave");
    }

    @Test void instructorSeesAssignedProgramsAndFicha() {
        User user = user(); var instructor = new Instructor(); instructor.setIdInstructor(UUID.randomUUID());
        when(instructors.findByUser_IdUser(user.getIdUser())).thenReturn(Optional.of(instructor));
        var first = new Program(); first.setProgramName("Software"); var second = new Program(); second.setProgramName("Redes");
        var a = new InstructorProgram(); a.setProgram(first); var b = new InstructorProgram(); b.setProgram(second);
        when(programs.findByInstructor_IdInstructor(instructor.getIdInstructor())).thenReturn(List.of(a, b));
        var chip = new Chip(); chip.setChipCode("2222222"); var relation = new InstructorChip(); relation.setChip(chip);
        when(chips.findByInstructor_IdInstructorAndActiveTrue(instructor.getIdInstructor())).thenReturn(List.of(relation));
        assertThat(template.render(user, "Instructor", "aprendiz@example.com", password, login, recovery, false)).contains("Instructor", "Software, Redes", "2222222");
    }

    @Test void userValuesAndActionUrlAreEscapedAsHtml() {
        User user = user(); user.setFirstName("Ana <script>alert(1)</script> & \"Ejemplo\"");
        String html = template.render(user, "Aprendiz", "aprendiz@example.com", password, login, recovery, false);
        assertThat(html).doesNotContain("<script>").contains("&lt;script&gt;", "&amp;", "&quot;", "&amp;lang=");
    }

    @Test void existingMailSenderKeepsHtmlAndInlineLogoInMimeMessage() throws Exception {
        var sender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(message);
        String html = template.render(user(), "Aprendiz", "aprendiz@example.com", password, login, recovery, false);
        new EmailServiceImpl(sender, "sender@example.invalid").sendInitialCredentials("aprendiz@example.com", "FaceLit - Credenciales iniciales", html);
        verify(sender).send(message);
        message.saveChanges();
        var parts = new ArrayList<Part>(); collect(message, parts);
        boolean hasHtml = false; boolean hasLogo = false;
        for (Part part : parts) {
            if (part.isMimeType("text/html")) { assertThat(part.getContent().toString()).isEqualTo(html); hasHtml = true; }
            String[] ids = part.getHeader("Content-ID");
            if (ids != null && Arrays.stream(ids).anyMatch(id -> id.contains("facelitLogo"))) hasLogo = true;
        }
        assertThat(hasHtml).isTrue(); assertThat(hasLogo).isTrue();
    }

    void collect(Part part, List<Part> parts) throws Exception {
        parts.add(part);
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) collect(multipart.getBodyPart(i), parts);
        }
    }
}
