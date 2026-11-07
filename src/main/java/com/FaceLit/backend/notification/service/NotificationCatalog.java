package com.FaceLit.backend.notification.service;

import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class NotificationCatalog {

    public record NotificationDefinition(String category, String channel, String defaultTitle) {
    }

    private static final String APP = "APP";
    private static final String APP_EMAIL = "APP_EMAIL";

    private final Map<String, NotificationDefinition> definitions = Map.ofEntries(
            Map.entry("attendance_corrected", new NotificationDefinition("ATTENDANCE", APP_EMAIL, "Asistencia corregida")),
            Map.entry("facial_authorization_expired", new NotificationDefinition("FACIAL_RECOGNITION", APP, "Permiso facial vencido")),
            Map.entry("user_role_changed", new NotificationDefinition("SECURITY", APP_EMAIL, "Rol de usuario modificado")),
            Map.entry("email_delivery_failed", new NotificationDefinition("SECURITY", APP, "Correo no entregado")),
            Map.entry("csv_upload_done", new NotificationDefinition("ACADEMIC", APP, "Carga de CSV finalizada")),
            Map.entry("csv_inconsistency", new NotificationDefinition("ACADEMIC", APP_EMAIL, "Inconsistencia pendiente de revision")),
            Map.entry("csv_transfer_applied", new NotificationDefinition("TRANSFER", APP, "Cambio de ficha aplicado")),
            Map.entry("csv_transfer_rejected", new NotificationDefinition("TRANSFER", APP, "Cambio de ficha rechazado")),
            Map.entry("csv_ref_error", new NotificationDefinition("ACADEMIC", APP, "Fila con error de referencia")),
            Map.entry("learner_transferred", new NotificationDefinition("TRANSFER", APP_EMAIL, "Traslado de aprendiz")),
            Map.entry("attendance_absent", new NotificationDefinition("ATTENDANCE", APP_EMAIL, "Inasistencia registrada")),
            Map.entry("attendance_late", new NotificationDefinition("ATTENDANCE", APP_EMAIL, "Retraso registrado")),
            Map.entry("attendance_early_exit", new NotificationDefinition("ATTENDANCE", APP_EMAIL, "Salida anticipada")),
            Map.entry("attendance_no_exit", new NotificationDefinition("ATTENDANCE", APP, "Salida no registrada")),
            Map.entry("attendance_wrong_env", new NotificationDefinition("ATTENDANCE", APP_EMAIL, "Registro en sesion no correspondiente")),
            Map.entry("attendance_excuse_registered", new NotificationDefinition("ATTENDANCE", APP_EMAIL, "Excusa registrada")),
            Map.entry("attendance_exit_reminder", new NotificationDefinition("ATTENDANCE", "EMAIL", "Recordatorio de salida")),
            Map.entry("attendance_session_summary", new NotificationDefinition("ATTENDANCE", APP_EMAIL, "Resumen de asistencia")),
            Map.entry("session_assigned", new NotificationDefinition("ATTENDANCE", APP_EMAIL, "Nueva sesion asignada")),
            Map.entry("session_updated", new NotificationDefinition("ATTENDANCE", APP_EMAIL, "Sesion actualizada")),
            Map.entry("session_cancelled", new NotificationDefinition("ATTENDANCE", APP_EMAIL, "Sesion cancelada")),
            Map.entry("attendance_substitute", new NotificationDefinition("ATTENDANCE", APP, "Sesion abierta por suplencia")),
            Map.entry("academic_delete_blocked", new NotificationDefinition("ACADEMIC", APP, "Intento de eliminacion bloqueado")),
            Map.entry("academic_entity_created", new NotificationDefinition("ACADEMIC", APP, "Registro academico creado")),
            Map.entry("academic_entity_updated", new NotificationDefinition("ACADEMIC", APP, "Registro academico actualizado")),
            Map.entry("academic_entity_activated", new NotificationDefinition("ACADEMIC", APP, "Registro academico activado")),
            Map.entry("academic_entity_deactivated", new NotificationDefinition("ACADEMIC", APP, "Registro academico desactivado")),
            Map.entry("academic_entity_deleted", new NotificationDefinition("ACADEMIC", APP, "Registro academico eliminado")),
            Map.entry("user_account_created", new NotificationDefinition("SECURITY", APP, "Tu usuario fue creado")),
            Map.entry("user_profile_updated", new NotificationDefinition("SECURITY", APP_EMAIL, "Tus datos fueron modificados")),
            Map.entry("instructor_profile_updated", new NotificationDefinition("ACADEMIC", APP_EMAIL, "Tus datos fueron modificados")),
            Map.entry("instructor_assignment_updated", new NotificationDefinition("ACADEMIC", APP, "Tu asignacion academica cambio")),
            Map.entry("apprentice_profile_updated", new NotificationDefinition("ACADEMIC", APP_EMAIL, "Tus datos fueron modificados")),
            Map.entry("facial_session_substitution", new NotificationDefinition("FACIAL_RECOGNITION", APP_EMAIL, "Sesión configurada para otro instructor")),
            Map.entry("facial_request_resolved", new NotificationDefinition("FACIAL_RECOGNITION", APP_EMAIL, "Respuesta a tu solicitud facial")),
            Map.entry("facial_registration_completed", new NotificationDefinition("FACIAL_RECOGNITION", APP, "Tu registro facial fue completado")),
            Map.entry("password_changed", new NotificationDefinition("SECURITY", APP_EMAIL, "Tu contrasena fue modificada")),
            Map.entry("account_status_changed", new NotificationDefinition("SECURITY", APP_EMAIL, "Aviso sobre el estado de tu cuenta")),
            Map.entry("apprentice_transfer_applied", new NotificationDefinition("TRANSFER", APP_EMAIL, "Cambio de ficha registrado")),
            Map.entry("security_multiple_failures", new NotificationDefinition("SECURITY", APP_EMAIL, "Multiples intentos fallidos")),
            Map.entry("security_account_locked", new NotificationDefinition("SECURITY", APP_EMAIL, "Cuenta bloqueada")),
            Map.entry("facial_reregister_request", new NotificationDefinition("FACIAL_RECOGNITION", APP_EMAIL, "Solicitud de re-registro facial"))
    );

    public NotificationDefinition require(String type) {
        NotificationDefinition definition = definitions.get(type);
        if (definition == null) {
            throw new IllegalArgumentException("Tipo de notificacion no soportado: " + type);
        }
        return definition;
    }
}
