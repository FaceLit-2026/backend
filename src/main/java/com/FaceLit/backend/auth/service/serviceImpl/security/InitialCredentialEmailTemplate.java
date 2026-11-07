package com.FaceLit.backend.auth.service.serviceImpl.security;

import com.FaceLit.backend.academic.model.enums.AcademicState;
import com.FaceLit.backend.academic.repository.*;
import com.FaceLit.backend.auth.model.security.User;
import java.util.LinkedHashSet;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Shared original presentation for registration and administrative email updates. */
@Component
public class InitialCredentialEmailTemplate {
    private final UserChipRepository userChips;
    private final InstructorRepository instructors;
    private final InstructorProgramRepository instructorPrograms;
    private final InstructorChipRepository instructorChips;

    public InitialCredentialEmailTemplate(UserChipRepository userChips, InstructorRepository instructors,
            InstructorProgramRepository instructorPrograms, InstructorChipRepository instructorChips) {
        this.userChips = userChips; this.instructors = instructors;
        this.instructorPrograms = instructorPrograms; this.instructorChips = instructorChips;
    }

    public String render(User user, String roleLabel, String email, String password, String loginUrl,
            String recoveryUrl, boolean emailUpdated) {
        var fichaCodes = new LinkedHashSet<String>();
        var programNames = new LinkedHashSet<String>();
        userChips.findByApprentice_User_IdUserAndState(user.getIdUser(), AcademicState.ACTIVE).ifPresent(assignment -> {
            if (assignment.getChip() != null) {
                fichaCodes.add(assignment.getChip().getChipCode());
                if (assignment.getChip().getProgram() != null) programNames.add(assignment.getChip().getProgram().getProgramName());
            }
        });
        instructors.findByUser_IdUser(user.getIdUser()).ifPresent(instructor -> {
            instructorPrograms.findByInstructor_IdInstructor(instructor.getIdInstructor()).forEach(assignment -> {
                if (assignment.getProgram() != null) programNames.add(assignment.getProgram().getProgramName());
            });
            instructorChips.findByInstructor_IdInstructorAndActiveTrue(instructor.getIdInstructor()).forEach(assignment -> {
                if (assignment.getChip() != null) fichaCodes.add(assignment.getChip().getChipCode());
            });
        });
        fichaCodes.removeIf(value -> value == null || value.isBlank());
        programNames.removeIf(value -> value == null || value.isBlank());
        String fullName = (Objects.toString(user.getFirstName(), "") + " " + Objects.toString(user.getLastName(), "")).trim();
        return """
                <!doctype html>
                <html lang="es">
                <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1"></head>
                <body style="margin:0;padding:0;background:#edf4f1;font-family:Arial,Helvetica,sans-serif;color:#123026;">
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#edf4f1;padding:28px 12px;">
                    <tr><td align="center">
                      <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:620px;background:#ffffff;border-radius:24px;overflow:hidden;box-shadow:0 18px 45px rgba(0,79,48,.22);">
                        <tr><td style="background:#087a4f;background:linear-gradient(135deg,#023b27 0%%,#087a4f 55%%,#16c98d 100%%);padding:30px 24px;text-align:center;">
                          <div style="display:inline-block;width:86%%;max-width:390px;border-radius:28px;padding:16px 20px;background:rgba(255,255,255,.13);box-shadow:0 16px 36px rgba(0,0,0,.28),0 0 34px rgba(25,255,174,.35);">
                            <img src="cid:facelitLogo" width="340" alt="FaceLit" style="display:block;width:100%%;max-width:340px;height:auto;margin:0 auto;filter:drop-shadow(0 10px 18px rgba(0,0,0,.35));">
                          </div>
                          <h1 style="margin:18px 0 6px;font-size:28px;line-height:1.15;color:#ffffff;font-weight:800;">Credenciales de acceso</h1>
                          <p style="margin:0;color:#dbfff2;font-size:15px;">Tu acceso a FaceLit ya está listo</p>
                        </td></tr>
                        <tr><td style="padding:30px 26px 26px;">
                          <p style="margin:0 0 16px;font-size:18px;line-height:1.5;">Hola <strong>%s</strong>,</p>
                          <p style="margin:0 0 22px;font-size:15px;line-height:1.6;color:#36584d;">%s Estos son tus datos para iniciar sesión con tu número de documento y la contraseña generada por el sistema.</p>
                          <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="border-collapse:separate;border-spacing:0 10px;">
                            %s%s%s%s%s%s
                          </table>
                          <div style="margin:22px 0;padding:18px;border-radius:18px;background:#f0fff8;border:1px solid #b9f4da;">
                            <div style="font-size:12px;letter-spacing:.08em;text-transform:uppercase;color:#087a4f;font-weight:800;margin-bottom:8px;">Contraseña de acceso</div>
                            <div data-credential-password="true" style="font-family:monospace;font-size:20px;letter-spacing:.08em;line-height:1.6;color:#023b27;font-weight:700;">%s</div>
                          </div>
                          <div style="text-align:center;margin:26px 0 20px;">
                            <a href="%s" style="display:inline-block;background:#087a4f;color:#ffffff;text-decoration:none;border-radius:999px;padding:14px 26px;font-size:15px;font-weight:800;box-shadow:0 10px 20px rgba(8,122,79,.25);">Ingresar a FaceLit</a>
                          </div>
                          <p style="margin:0;color:#58746a;font-size:13px;line-height:1.6;text-align:center;">Puedes cambiar tu contraseña cuando quieras desde <a href="%s" style="color:#087a4f;font-weight:700;">¿Olvidaste tu contraseña?</a>, en el inicio de sesión. Ingresa el correo mostrado arriba, recibe el código de seis dígitos, verifícalo y elige tu nueva contraseña.</p>
                        </td></tr>
                        <tr><td style="background:#f7fbf9;padding:16px 24px;text-align:center;color:#789188;font-size:12px;">FaceLit · Gestión académica segura</td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body></html>
                """.formatted(escape(user.getFirstName()), emailUpdated ? "Tu correo electrónico fue actualizado correctamente. Esta contraseña reemplaza la anterior."
                        : "Tu cuenta fue creada correctamente.", detailRow("Nombre", fullName), detailRow("Rol", roleLabel),
                detailRow("Usuario de inicio de sesión", user.getDocumentNumber()), detailRow("Ficha", String.join(", ", fichaCodes)),
                detailRow("Programa", String.join(", ", programNames)), detailRow("Correo electrónico", email),
                escape(password), escape(loginUrl), escape(recoveryUrl));
    }

    private static String detailRow(String label, String value) {
        if (value == null || value.isBlank()) return "";
        return """
                <tr><td style="padding:13px 14px;border-radius:14px;background:#f7fbf9;border:1px solid #e1eee9;">
                  <div style="font-size:12px;color:#6c887d;text-transform:uppercase;letter-spacing:.06em;font-weight:800;">%s</div>
                  <div style="margin-top:4px;font-size:16px;color:#123026;font-weight:700;">%s</div>
                </td></tr>
                """.formatted(escape(label), escape(value));
    }
    private static String escape(String value) {
        return Objects.toString(value, "").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
