package com.FaceLit.backend.shared.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import com.FaceLit.backend.shared.service.EmailService;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.core.io.ClassPathResource;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service // Marca esta clase como un servicio de Spring.
// Spring la detecta automáticamente y permite usarla en otras clases.
public class EmailServiceImpl implements EmailService {

    // JavaMailSender es la herramienta que usa Spring
    // para enviar correos electrónicos.

    // private → solo se puede usar dentro de esta clase
    // final → el valor no cambiará después de asignarse
    private final JavaMailSender mailSender;
    private final String fromAddress;

    // Constructor de la clase.
    // Spring envía automáticamente el JavaMailSender
    // para poder usar el servicio de correos.
    public EmailServiceImpl(JavaMailSender mailSender,
            @Value("${spring.mail.username}") String fromAddress) {

        // this.mailSender → variable de la clase
        // mailSender → valor recibido en el constructor
        // Aquí se guarda la herramienta de envío de correos.
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public void sendRecoveryCode(String toEmail, String code) {
        String subject = "FaceLit - Codigo de recuperacion de contrasena";
        String html = buildRecoveryCodeEmail(code);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(
                    "Hola,\n\n" +
                            "Recibimos una solicitud para restablecer tu contrasena.\n" +
                            "Tu codigo de recuperacion es: " + code + "\n\n" +
                            "Este codigo expira en 5 minutos.\n" +
                            "Si no solicitaste este cambio, ignora este mensaje.\n\n" +
                            "FaceLit",
                    html);
            helper.addInline("facelitLogo", new ClassPathResource("static/email/facelit-logo.png"), "image/png");
            mailSender.send(message);
        } catch (MessagingException exception) {
            throw new IllegalStateException("No fue posible construir el correo de recuperacion de contrasena", exception);
        }
    }

    @Override
    public void sendNotificationEmail(String toEmail, String subject, String body) {
        String html = buildNotificationEmail(subject, body);
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(body, html);
            helper.addInline("facelitLogo", new ClassPathResource("static/email/facelit-logo.png"), "image/png");
            mailSender.send(message);
        } catch (MessagingException exception) {
            throw new IllegalStateException("No fue posible construir el correo de notificacion", exception);
        }
    }

    @Override
    public void sendInitialCredentials(String toEmail, String subject, String body) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText("Tus datos de acceso a FaceLit están disponibles en la versión HTML de este correo. Inicia sesión con tu número de documento y la contraseña generada. Puedes cambiarla cuando quieras desde ¿Olvidaste tu contraseña? en el inicio de sesión.", body);
            helper.addInline("facelitLogo", new ClassPathResource("static/email/facelit-logo.png"), "image/png");
            mailSender.send(message);
        } catch (MessagingException exception) {
            throw new IllegalStateException("No fue posible construir el correo de credenciales iniciales", exception);
        }
    }

    private String buildRecoveryCodeEmail(String code) {
        return "<!doctype html>" +
                "<html lang=\"es\">" +
                "<head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"></head>" +
                "<body style=\"margin:0;padding:0;background:#eef7f0;font-family:Arial,Helvetica,sans-serif;color:#122033;\">" +
                "<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"background:#eef7f0;padding:28px 12px;\">" +
                "<tr><td align=\"center\">" +
                "<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"max-width:620px;background:#ffffff;border-radius:26px;overflow:hidden;box-shadow:0 18px 45px rgba(22,70,42,0.18);\">" +
                "<tr><td style=\"background:#0f6d3b;background:linear-gradient(135deg,#064d2d,#12884f 58%,#65b361);padding:34px 28px 56px;text-align:center;\">" +
                "<img src=\"cid:facelitLogo\" alt=\"FaceLit\" width=\"220\" style=\"display:block;margin:0 auto 24px;max-width:76%;filter:drop-shadow(0 12px 24px rgba(0,0,0,0.28));\">" +
                "<div style=\"display:inline-block;padding:8px 14px;border-radius:999px;background:rgba(255,255,255,0.18);color:#ffffff;font-size:12px;font-weight:800;letter-spacing:.6px;text-transform:uppercase;\">" +
                "Verificacion segura" +
                "</div>" +
                "<h1 style=\"margin:18px 0 0;color:#ffffff;font-size:28px;line-height:1.18;font-weight:900;\">Codigo para cambiar tu contrasena</h1>" +
                "<p style=\"margin:12px auto 0;max-width:430px;color:#e7fff0;font-size:15px;line-height:1.6;\">Usa este codigo unico para confirmar tu identidad y continuar con el cambio de contrasena.</p>" +
                "</td></tr>" +
                "<tr><td style=\"padding:0 28px 30px;\">" +
                "<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"margin-top:-34px;background:#ffffff;border:1px solid #d7eadc;border-radius:22px;box-shadow:0 14px 30px rgba(18,32,51,0.12);\">" +
                "<tr><td style=\"padding:26px 22px;text-align:center;\">" +
                "<p style=\"margin:0 0 10px;color:#5f6f86;font-size:14px;\">Tu codigo de recuperacion es</p>" +
                "<div style=\"display:inline-block;padding:16px 26px;border-radius:18px;background:#f1fbf3;border:1px dashed #65b361;color:#0f6d3b;font-size:34px;line-height:1;font-weight:900;letter-spacing:8px;\">" +
                code +
                "</div>" +
                "<p style=\"margin:18px 0 0;color:#122033;font-size:15px;line-height:1.55;\">Este codigo expira en <strong>5 minutos</strong>. Despues de verificarlo podras crear una nueva contrasena.</p>" +
                "</td></tr></table>" +
                "<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"margin-top:18px;background:#fff8e8;border:1px solid #f2d79a;border-radius:18px;\">" +
                "<tr><td style=\"padding:16px 18px;color:#7a5315;font-size:14px;line-height:1.55;\">" +
                "<strong>Si no solicitaste este cambio,</strong> ignora este correo. Tu contrasena actual seguira funcionando y nadie podra cambiarla sin este codigo." +
                "</td></tr></table>" +
                "<p style=\"margin:22px 0 0;color:#7a8799;font-size:12px;line-height:1.5;text-align:center;\">FaceLit protege tu cuenta con verificacion por correo y codigos de un solo uso.</p>" +
                "</td></tr></table>" +
                "</td></tr></table>" +
                "</body></html>";
    }

    private String buildNotificationEmail(String subject, String body) {
        String safeSubject = escapeHtml(subject == null || subject.isBlank() ? "Notificacion FaceLit" : subject);
        String safeBody = escapeHtml(body == null ? "" : body)
                .replaceAll("(https?://[^\\s<]+)", "<a href=\"$1\" style=\"color:#0f6d3b;word-break:break-all;\">$1</a>")
                .replace("\n", "<br>");
        boolean transfer = safeSubject.toLowerCase().contains("ficha") || safeSubject.toLowerCase().contains("traslado")
                || safeBody.toLowerCase().contains("traslad");
        String badge = transfer ? "Traslado academico" : "Notificacion";
        String icon = transfer ? "&#8644;" : "&#9432;";
        String accent = transfer ? "#8B5CF6" : "#65b361";
        String accentSoft = transfer ? "#f4efff" : "#f1fbf3";
        String accentBorder = transfer ? "#d8c8ff" : "#d7eadc";

        return "<!doctype html>" +
                "<html lang=\"es\">" +
                "<head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"></head>" +
                "<body style=\"margin:0;padding:0;background:#eef7f0;font-family:Arial,Helvetica,sans-serif;color:#122033;\">" +
                "<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"background:#eef7f0;padding:28px 12px;\">" +
                "<tr><td align=\"center\">" +
                "<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"max-width:640px;background:#ffffff;border-radius:26px;overflow:hidden;box-shadow:0 18px 45px rgba(22,70,42,0.18);\">" +
                "<tr><td style=\"background:#0f6d3b;background:linear-gradient(135deg,#064d2d,#12884f 58%,#65b361);padding:32px 28px 54px;text-align:center;\">" +
                "<img src=\"cid:facelitLogo\" alt=\"FaceLit\" width=\"220\" style=\"display:block;margin:0 auto 22px;max-width:76%;filter:drop-shadow(0 12px 24px rgba(0,0,0,0.28));\">" +
                "<div style=\"display:inline-block;padding:8px 14px;border-radius:999px;background:rgba(255,255,255,0.18);color:#ffffff;font-size:12px;font-weight:800;letter-spacing:.6px;text-transform:uppercase;\">" +
                badge +
                "</div>" +
                "<h1 style=\"margin:18px 0 0;color:#ffffff;font-size:28px;line-height:1.2;font-weight:900;\">" +
                safeSubject.replace("FaceLit - ", "") +
                "</h1>" +
                "<p style=\"margin:12px auto 0;max-width:450px;color:#e7fff0;font-size:15px;line-height:1.6;\">FaceLit registra y comunica este evento para mantenerte informado oportunamente.</p>" +
                "</td></tr>" +
                "<tr><td style=\"padding:0 28px 30px;\">" +
                "<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"margin-top:-34px;background:#ffffff;border:1px solid " + accentBorder + ";border-radius:22px;box-shadow:0 14px 30px rgba(18,32,51,0.12);\">" +
                "<tr><td style=\"padding:26px 22px;\">" +
                "<table role=\"presentation\" cellspacing=\"0\" cellpadding=\"0\"><tr>" +
                "<td style=\"width:56px;height:56px;border-radius:18px;background:" + accentSoft + ";color:" + accent + ";font-size:28px;font-weight:900;text-align:center;vertical-align:middle;\">" + icon + "</td>" +
                "<td style=\"padding-left:16px;vertical-align:middle;\">" +
                "<p style=\"margin:0 0 6px;color:#5f6f86;font-size:13px;font-weight:800;text-transform:uppercase;letter-spacing:.5px;\">Detalle de la notificacion</p>" +
                "<p style=\"margin:0;color:#122033;font-size:16px;line-height:1.55;font-weight:700;\">" + safeBody + "</p>" +
                "</td></tr></table>" +
                "</td></tr></table>" +
                "<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"margin-top:18px;background:#f8fbff;border:1px solid #dce7f3;border-radius:18px;\">" +
                "<tr><td style=\"padding:16px 18px;color:#5f6f86;font-size:14px;line-height:1.55;\">" +
                "Este mensaje fue generado automaticamente por FaceLit. Puedes revisar el detalle completo en el centro de notificaciones de la plataforma." +
                "</td></tr></table>" +
                "<p style=\"margin:22px 0 0;color:#7a8799;font-size:12px;line-height:1.5;text-align:center;\">FaceLit - Gestion academica y asistencia inteligente.</p>" +
                "</td></tr></table>" +
                "</td></tr></table>" +
                "</body></html>";
    }

    private String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
