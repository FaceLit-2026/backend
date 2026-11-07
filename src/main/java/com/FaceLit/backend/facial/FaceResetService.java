package com.FaceLit.backend.facial;

import java.time.OffsetDateTime;
import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import com.FaceLit.backend.academic.model.enums.AcademicState;
import com.FaceLit.backend.academic.repository.UserChipRepository;
import com.FaceLit.backend.facial.model.FaceResetRequest;
import com.FaceLit.backend.facial.repository.FaceResetRequestRepository;
import com.FaceLit.backend.notification.repository.NotificationRepository;
import com.FaceLit.backend.shared.service.UserDisplayNameService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FaceResetService {
    private static final Logger LOG = LoggerFactory.getLogger(FaceResetService.class);
    private final FaceResetRequestRepository requests;
    private final UserChipRepository assignments;
    private final NotificationRepository notifications;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();
    private final int authorizationHours;
    private final UserDisplayNameService names;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public FaceResetService(FaceResetRequestRepository requests, UserChipRepository assignments,
            NotificationRepository notifications, JdbcTemplate jdbc,
            @Value("${facial.reset.authorization-hours:24}") int authorizationHours, UserDisplayNameService names) {
        this(requests, assignments, notifications, jdbc, authorizationHours, names, Clock.systemUTC());
    }

    FaceResetService(FaceResetRequestRepository requests, UserChipRepository assignments,
            NotificationRepository notifications, JdbcTemplate jdbc, int authorizationHours,
            UserDisplayNameService names, Clock clock) {
        this.requests = requests;
        this.assignments = assignments;
        this.notifications = notifications;
        this.jdbc = jdbc;
        this.authorizationHours = Math.max(1, authorizationHours);
        this.names = names;
        this.clock = clock;
    }

    public record ResetState(UUID requestId, String status, boolean canReregister,
            OffsetDateTime expiresAt, UUID authorizationToken) {}

    public ResetState state(FaceResetRequest request) {
        return new ResetState(request.getId(), request.getStatus(), request.canRegister(),
                request.getAuthorizationExpiresAt(), request.canRegister() ? request.getAuthorizationToken() : null);
    }

    @Transactional(readOnly = true)
    public ResetState current(UUID userId) {
        return requests.findFirstByUserIdOrderByRequestedAtDesc(userId).map(this::state)
                .orElse(new ResetState(null, "NONE", false, null, null));
    }

    @Transactional
    public ResetState create(UUID userId) {
        // The same lock is used by the embedding writer to serialize request/registration races.
        jdbc.queryForObject("SELECT id_user_app FROM security.user_app WHERE id_user_app = ? FOR UPDATE", UUID.class, userId);
        FaceResetRequest last = requests.findFirstByUserIdOrderByRequestedAtDesc(userId).orElse(null);
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (last != null && now.isBefore(last.getRequestedAt().plusDays(7))) {
            String availableAt = last.getRequestedAt().plusDays(7).atZoneSameInstant(ZoneId.of("America/Bogota"))
                    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Ya enviaste una solicitud al coordinador. Podrás enviar una nueva el "
                            + availableAt + " (hora de Colombia).");
        }
        if (last != null && ("PENDING".equals(last.getStatus()) || last.canRegister())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya tienes una solicitud pendiente o una autorización vigente.");
        }
        boolean registered = Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM facialrecognition.user_face f
                JOIN academic.apprentice a ON a.id_apprentice = f.id_apprentice
                WHERE a.id_user_app = ? AND a.deleted_at IS NULL AND f.status = 'ACTIVE' AND f.deleted_at IS NULL)
                """, Boolean.class, userId));
        if (!registered) throw new ResponseStatusException(HttpStatus.CONFLICT, "Debes tener un registro facial previo.");
        var assignment = assignments.findByApprentice_User_IdUserAndState(userId, AcademicState.ACTIVE)
                .filter(item -> item.getDeletedAt() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "No tienes una ficha activa asignada."));
        var user = assignment.getApprentice().getUser();
        FaceResetRequest request = new FaceResetRequest();
        request.setUserId(userId);
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("requestId", request.getId().toString());
        context.put("facialUserId", userId.toString());
        context.put("learnerName", (user.getFirstName() + " " + user.getLastName()).trim());
        context.put("learnerDocument", user.getDocumentNumber());
        context.put("fichaNumber", assignment.getChip().getChipCode());
        context.put("programName", assignment.getChip().getProgram().getProgramName());
        request.setContextJson(writeJson(context));
        request.setRequestedAt(OffsetDateTime.now(clock));
        requests.save(request);
        LOG.info("Solicitud facial creada. request={} user={}", request.getId(), userId);
        return state(request);
    }

    @Transactional
    public void resolve(UUID notificationId, UUID actor, String decision) {
        var notification = notifications.findById(notificationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notificación no encontrada."));
        if (!actor.equals(notification.getIdUserApp()) || !"facial_reregister_request".equals(notification.getType())
                || !"face_reset_request".equals(notification.getReferenceEntity())) {
            LOG.warn("Resolución facial fuera de alcance. actor={} notification={}", actor, notificationId);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes resolver esta solicitud.");
        }
        if (!"accepted".equals(decision) && !"rejected".equals(decision)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Decisión no válida.");
        }
        var request = requests.lockById(notification.getReferenceId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitud no encontrada."));
        if (!"PENDING".equals(request.getStatus())) {
            LOG.warn("Segunda resolución facial impedida. actor={} request={} status={}", actor, request.getId(), request.getStatus());
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La solicitud ya fue resuelta.");
        }
        request.setStatus("accepted".equals(decision) ? "ACCEPTED" : "REJECTED");
        request.setResolvedBy(actor);
        request.setResolvedAt(OffsetDateTime.now());
        Map<String, Object> decisionContext = readJson(request.getContextJson());
        decisionContext.put("decidedByName", names.fullName(actor));
        request.setContextJson(writeJson(decisionContext));
        if ("accepted".equals(decision)) {
            request.setAuthorizationToken(UUID.randomUUID());
            request.setAuthorizationExpiresAt(request.getResolvedAt().plusHours(authorizationHours));
        }
        requests.save(request);
        // Update every coordinator copy. The domain decision is saved independently of delivery.
        for (var copy : notifications.findByReferenceEntityAndReferenceId("face_reset_request", request.getId())) {
            if (!"facial_reregister_request".equals(copy.getType())) continue;
            Map<String, Object> metadata = readJson(request.getContextJson());
            metadata.put("facialDecision", decision);
            metadata.put("decidedBy", actor.toString());
            metadata.put("decidedAt", request.getResolvedAt().toString());
            copy.setMetadataJson(writeJson(metadata));
            copy.markRead();
            notifications.save(copy);
        }
        LOG.info("Solicitud facial resuelta. request={} actor={} decision={}", request.getId(), actor, decision);
    }

    public Map<String, Object> readJson(String source) {
        try { return json.readValue(source, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Contexto facial inválido", ex); }
    }

    public String coordinatorName(UUID actor) { return names.fullName(actor); }

    public String writeJson(Map<String, Object> value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("No se pudo guardar el contexto facial", ex); }
    }
}
