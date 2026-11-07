package com.FaceLit.backend.shared.constants;

public final class AppConstants {

    // ── Códigos de verificación ──────────────────────────────
    public static final int VERIFICATION_CODE_LENGTH = 6;
    public static final int VERIFICATION_CODE_MAX = 999999;
    public static final int VERIFICATION_EXPIRY_MINUTES = 5;

    public static final int MAX_LOGIN_ATTEMPTS = 3;
    public static final int LOGIN_LOCK_MINUTES = 15;

    // ── NUEVO — Reenvío de códigos (cooldown) ────────────────
    public static final int RESEND_COOLDOWN_SECONDS = 60;

    // ── JWT ──────────────────────────────────────────────────
    public static final String ROLE_PREFIX = "ROLE_";
    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";
    public static final String JWT_CLAIM_USER_ID = "userId";
    public static final String JWT_CLAIM_EMAIL = "email";
    public static final String JWT_CLAIM_ROLE = "role";
    public static final String JWT_CLAIM_PERMISSIONS = "permissions";

    // ── Paginación por defecto ───────────────────────────────
    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_PAGE_SIZE = 10;

    // ── Estados ──────────────────────────────────────────────
    public static final String ACTIVE = "ACTIVE";
    public static final String INACTIVE = "INACTIVE";

    public static final int JWT_EXPIRY_HOURS = 8;
    public static final int PASSWORD_RECOVERY_EXPIRY_MINUTES = 5;

    // ── Carga académica CSV ─────────────────────────────────
    // Se usa tanto para la validación del backend como para la UI.
    public static final long CSV_MAX_FILE_BYTES = 20L * 1024 * 1024;
    public static final int CSV_MAX_DATA_ROWS = 5000;
    public static final int DOCUMENT_NUMBER_MIN_LENGTH = 6;
    public static final int DOCUMENT_NUMBER_MAX_LENGTH = 15;
    public static final int CHIP_CODE_LENGTH = 7;
    public static final int PROGRAM_CODE_MIN_LENGTH = 2;
    public static final int PROGRAM_CODE_MAX_LENGTH = 15;
    public static final String CSV_TYPE_COLUMN = "tipo";
    public static final String CSV_TEMPLATE_HEADER = "tipo,documento,nombre,apellido,correo,programa_codigo,ficha_codigo,instructor_tipo";

    private AppConstants() {
        // Evita instanciación — es solo un contenedor de constantes
    }
}
