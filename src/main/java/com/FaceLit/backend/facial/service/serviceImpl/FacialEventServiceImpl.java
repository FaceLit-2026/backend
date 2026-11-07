package com.FaceLit.backend.facial.service.serviceImpl;

import com.FaceLit.backend.academic.model.academic.Apprentice;
import com.FaceLit.backend.academic.model.enums.AcademicState;
import com.FaceLit.backend.academic.repository.ApprenticeRepository;
import com.FaceLit.backend.academic.repository.UserChipRepository;
import com.FaceLit.backend.environment.model.RecordEnvironment;
import com.FaceLit.backend.environment.repository.RecordEnvironmentRepository;
import com.FaceLit.backend.facial.FacialEmbeddingVerificationClient.VerificationResponse;
import com.FaceLit.backend.facial.FacialEmbeddingVerificationClient;
import com.FaceLit.backend.facial.dto.request.FacialAttendanceCaptureRequestDTO;
import com.FaceLit.backend.facial.dto.request.FacialEventRequestDTO;
import com.FaceLit.backend.facial.dto.request.UpdateFacialEventExcuseRequestDTO;
import com.FaceLit.backend.facial.dto.response.AttendanceMatrixResponseDTO;
import com.FaceLit.backend.facial.dto.response.AttendanceStatusResponseDTO;
import com.FaceLit.backend.facial.dto.response.FacialEventResponseDTO;
import com.FaceLit.backend.facial.exception.FacialEventException;
import com.FaceLit.backend.facial.model.Device;
import com.FaceLit.backend.facial.model.FacialEvent;
import com.FaceLit.backend.facial.model.enums.AttendanceStatus;
import com.FaceLit.backend.facial.model.enums.FacialEventOrigin;
import com.FaceLit.backend.facial.model.enums.FacialEventType;
import com.FaceLit.backend.facial.repository.AttendanceWriteRepository;
import com.FaceLit.backend.facial.repository.DeviceRepository;
import com.FaceLit.backend.facial.repository.FacialEventRepository;
import com.FaceLit.backend.facial.service.AttendanceMatrixService;
import com.FaceLit.backend.facial.service.AttendanceNotificationService;
import com.FaceLit.backend.facial.service.FacialAccessPolicy;
import com.FaceLit.backend.facial.service.FacialEventService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Service
public class FacialEventServiceImpl implements FacialEventService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FacialEventServiceImpl.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final FacialEventRepository facialEventRepository;
    private final RecordEnvironmentRepository recordEnvironmentRepository;
    private final DeviceRepository deviceRepository;
    private final ApprenticeRepository apprenticeRepository;
    private final UserChipRepository userChipRepository;
    private final FacialEmbeddingVerificationClient verificationClient;
    private final AttendanceMatrixService attendanceMatrixService;
    private final AttendanceNotificationService attendanceNotifications;
    private final FacialAccessPolicy accessPolicy;
    private final AttendanceWriteRepository attendanceWriteRepository;

    public FacialEventServiceImpl(
            FacialEventRepository facialEventRepository,
            RecordEnvironmentRepository recordEnvironmentRepository,
            DeviceRepository deviceRepository,
            ApprenticeRepository apprenticeRepository,
            UserChipRepository userChipRepository,
            FacialEmbeddingVerificationClient verificationClient,
            AttendanceMatrixService attendanceMatrixService,
            AttendanceNotificationService attendanceNotifications,
            FacialAccessPolicy accessPolicy,
            AttendanceWriteRepository attendanceWriteRepository) {
        this.facialEventRepository = facialEventRepository;
        this.recordEnvironmentRepository = recordEnvironmentRepository;
        this.deviceRepository = deviceRepository;
        this.apprenticeRepository = apprenticeRepository;
        this.userChipRepository = userChipRepository;
        this.verificationClient = verificationClient;
        this.attendanceMatrixService = attendanceMatrixService;
        this.attendanceNotifications = attendanceNotifications;
        this.accessPolicy = accessPolicy;
        this.attendanceWriteRepository = attendanceWriteRepository;
    }

    @Override
    @Transactional
    public FacialEventResponseDTO registerEvent(FacialEventRequestDTO dto, UUID authenticatedUserId) {
        RecordEnvironment record = recordEnvironmentRepository.findById(dto.getIdRecordEnvironment())
                .orElseThrow(() -> new FacialEventException("Sesión no encontrada.", HttpStatus.NOT_FOUND));
        accessPolicy.requireSessionOperator(record, authenticatedUserId);
        if (!Boolean.TRUE.equals(record.getActive()) || record.getDeletedAt() != null) {
            throw new FacialEventException("La sesión no está activa.", HttpStatus.CONFLICT);
        }
        Device device = deviceRepository.findById(dto.getIdDevice())
                .orElseThrow(() -> new FacialEventException("Dispositivo no encontrado.", HttpStatus.NOT_FOUND));
        Apprentice apprentice = apprenticeRepository.findById(dto.getIdApprentice())
                .orElseThrow(() -> new FacialEventException("Aprendiz no encontrado.", HttpStatus.NOT_FOUND));

        if (!device.getIdDevice().equals(record.getDevice().getIdDevice())) {
            throw new FacialEventException("El dispositivo no pertenece a la sesión indicada.", HttpStatus.BAD_REQUEST);
        }
        if (!userChipRepository.existsByApprentice_IdApprenticeAndChip_IdChipAndState(
                apprentice.getIdApprentice(),
                record.getChip().getIdChip(),
                AcademicState.ACTIVE)) {
            throw new FacialEventException("El aprendiz no pertenece a la ficha activa de esta sesión.", HttpStatus.BAD_REQUEST);
        }

        OffsetDateTime eventDatetime = dto.getEventDatetime() == null ? OffsetDateTime.now() : dto.getEventDatetime();
        FacialEventType eventType = resolveEventType(eventDatetime, record);
        if (dto.getEventType() != null) {
            eventType = dto.getEventType();
            if (eventType == FacialEventType.ENTRY && !eventDatetime.isBefore(record.getExitTime())) {
                throw new FacialEventException("La ventana de entrada ya terminó.", HttpStatus.BAD_REQUEST);
            }
        }
        if (eventType == FacialEventType.EXIT) {
            boolean attended = facialEventRepository
                        .findFirstByRecordEnvironment_IdRecordEnvironmentAndApprentice_IdApprenticeAndEventTypeAndDeletedAtIsNullOrderByEventDatetimeAsc(
                                record.getIdRecordEnvironment(), apprentice.getIdApprentice(), FacialEventType.ENTRY)
                        .filter(entry -> entry.getAttendanceStatus() != AttendanceStatus.ABSENT
                                && entry.getEventDatetime().isBefore(eventDatetime)).isPresent();
            if (!attended) throw new FacialEventException("Debes registrar una entrada antes de registrar la salida.", HttpStatus.BAD_REQUEST);
        }
        AttendanceStatus attendanceStatus = resolveAttendanceStatus(eventType, eventDatetime, record, apprentice);

        FacialEvent event = facialEventRepository
                .findFirstByRecordEnvironment_IdRecordEnvironmentAndApprentice_IdApprenticeAndEventTypeAndDeletedAtIsNullOrderByEventDatetimeAsc(
                        record.getIdRecordEnvironment(), apprentice.getIdApprentice(), eventType)
                .orElse(null);

        if (event != null && event.getAttendanceStatus() != AttendanceStatus.ABSENT) {
            throw new FacialEventException("La asistencia ya fue registrada anteriormente", HttpStatus.CONFLICT);
        }
        if (event == null) {
            event = new FacialEvent();
            event.setRecordEnvironment(record);
            event.setDevice(device);
            event.setApprentice(apprentice);
        }
        event.setEventDatetime(eventDatetime);
        event.setEventType(eventType);
        event.setRecognitionResult(dto.getRecognitionResult().trim());
        event.setAttendanceStatus(attendanceStatus);
        event.setMatchScore(dto.getMatchScore());
        event.setOrigin(parseOrigin(dto.getOrigin()));
        event.setCreatedBy(authenticatedUserId.toString());
        event.setExcuse(null);

        try {
            FacialEvent saved = facialEventRepository.saveAndFlush(event);
            attendanceNotifications.notifyAttendanceEvent(saved);
            return new FacialEventResponseDTO(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new FacialEventException("La asistencia ya fue registrada anteriormente", HttpStatus.CONFLICT);
        }
    }

    @Override
    @Transactional
    public void initializeSessionAbsences(UUID idRecordEnvironment, UUID authenticatedUserId) {
        RecordEnvironment record = recordEnvironmentRepository.findById(idRecordEnvironment)
                .orElseThrow(() -> new FacialEventException("Sesión no encontrada.", HttpStatus.NOT_FOUND));
        accessPolicy.requireSessionOperator(record, authenticatedUserId);

        attendanceWriteRepository.initializeSessionAbsences(authenticatedUserId, record.getIdRecordEnvironment());
    }

    @Override
    @Transactional
    public FacialEventResponseDTO registerEventFromImage(FacialAttendanceCaptureRequestDTO dto, UUID authenticatedUserId) {
        RecordEnvironment record = recordEnvironmentRepository.findById(dto.getIdRecordEnvironment())
                .orElseThrow(() -> new FacialEventException("Sesión no encontrada.", HttpStatus.NOT_FOUND));
        accessPolicy.requireSessionOperator(record, authenticatedUserId);
        if (!Boolean.TRUE.equals(record.getActive()) || record.getDeletedAt() != null) {
            throw new FacialEventException("La sesión no está activa.", HttpStatus.CONFLICT);
        }
        VerificationResponse verification;
        try {
            verification = verificationClient.verifySession(
                    dto.getIdRecordEnvironment(),
                    dto.getImageBase64(),
                    dto.getImageFrames(),
                    dto.getLivenessChallenge(),
                    dto.getLivenessChallenges());
        } catch (RestClientResponseException ex) {
            String detail = extractVerificationServiceDetail(ex);
            LOGGER.warn(
                    "El microservicio facial rechazo la verificacion. status={}, body={}",
                    ex.getStatusCode(),
                    detail);
            throw new FacialEventException(resolveVerificationServiceMessage(detail), HttpStatus.BAD_REQUEST);
        } catch (RestClientException ex) {
            LOGGER.warn("No fue posible conectar con el microservicio facial para verificar el rostro.", ex);
            throw new FacialEventException("No fue posible verificar el rostro capturado.", HttpStatus.BAD_GATEWAY);
        }

        if (verification == null || !verification.live()) {
            throw new FacialEventException(resolveLivenessFailureMessage(verification), HttpStatus.BAD_REQUEST);
        }

        if (!verification.match() || verification.idApprentice() == null) {
            throw new FacialEventException("Rostro no reconocido para esta ficha", HttpStatus.BAD_REQUEST);
        }

        FacialEventRequestDTO event = new FacialEventRequestDTO();
        event.setIdRecordEnvironment(dto.getIdRecordEnvironment());
        event.setIdDevice(dto.getIdDevice());
        event.setIdApprentice(verification.idApprentice());
        event.setRecognitionResult("MATCH");
        event.setEventType(dto.getEventType());
        event.setMatchScore(verification.similarity());
        event.setOrigin(dto.getOrigin() == null || dto.getOrigin().isBlank() ? "MOBILE" : dto.getOrigin());

        return registerEvent(event, authenticatedUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public AttendanceStatusResponseDTO getAttendanceStatus(UUID idRecordEnvironment, UUID idApprentice, UUID authenticatedUserId) {
        RecordEnvironment record = recordEnvironmentRepository.findById(idRecordEnvironment)
                .orElseThrow(() -> new FacialEventException("Sesión no encontrada.", HttpStatus.NOT_FOUND));
        Apprentice apprentice = apprenticeRepository.findById(idApprentice)
                .orElseThrow(() -> new FacialEventException("Aprendiz no encontrado.", HttpStatus.NOT_FOUND));
        if (accessPolicy.hasRole(authenticatedUserId, "APPRENTICE")) {
            if (!apprentice.getUser().getIdUser().equals(authenticatedUserId)
                    || !userChipRepository.existsByApprentice_IdApprenticeAndChip_IdChipAndState(
                            idApprentice, record.getChip().getIdChip(), AcademicState.ACTIVE)) {
                throw new FacialEventException("Solo puedes consultar tu propia asistencia.", HttpStatus.FORBIDDEN);
            }
        } else {
            accessPolicy.requireSessionOperator(record, authenticatedUserId);
        }

        AttendanceStatus status = facialEventRepository
                .findFirstByRecordEnvironment_IdRecordEnvironmentAndApprentice_IdApprenticeAndEventTypeAndDeletedAtIsNullOrderByEventDatetimeAsc(
                        idRecordEnvironment,
                        idApprentice,
                        FacialEventType.ENTRY)
                .map(FacialEvent::getAttendanceStatus)
                .orElse(AttendanceStatus.ABSENT);

        return new AttendanceStatusResponseDTO(idRecordEnvironment, idApprentice, status);
    }

    @Override
    @Transactional(readOnly = true)
    public AttendanceMatrixResponseDTO getAttendanceMatrix(UUID idChip, LocalDate dateFrom, LocalDate dateTo, UUID authenticatedUserId) {
        return attendanceMatrixService.getAttendanceMatrix(idChip, dateFrom, dateTo, authenticatedUserId);
    }

    @Override
    @Transactional
    public FacialEventResponseDTO updateExcuse(UpdateFacialEventExcuseRequestDTO dto, UUID authenticatedUserId) {
        RecordEnvironment record = recordEnvironmentRepository.findById(dto.getIdRecordEnvironment())
                .orElseThrow(() -> new FacialEventException("Sesión no encontrada.", HttpStatus.NOT_FOUND));
        accessPolicy.requireSessionOperator(record, authenticatedUserId);
        Apprentice apprentice = apprenticeRepository.findById(dto.getIdApprentice())
                .orElseThrow(() -> new FacialEventException("Aprendiz no encontrado.", HttpStatus.NOT_FOUND));

        if (!userChipRepository.existsByApprentice_IdApprenticeAndChip_IdChipAndState(
                apprentice.getIdApprentice(),
                record.getChip().getIdChip(),
                AcademicState.ACTIVE)) {
            throw new FacialEventException("El aprendiz no pertenece a la ficha activa de esta sesión.", HttpStatus.BAD_REQUEST);
        }

        FacialEvent event = facialEventRepository
                .findFirstByRecordEnvironment_IdRecordEnvironmentAndApprentice_IdApprenticeAndEventTypeAndDeletedAtIsNullOrderByEventDatetimeAsc(
                        record.getIdRecordEnvironment(),
                        apprentice.getIdApprentice(),
                        FacialEventType.ENTRY)
                .orElseGet(() -> buildAbsentEvent(record, apprentice, authenticatedUserId));
        boolean newAbsentEvent = event.getIdFacialEvent() == null;
        Boolean previousExcuse = event.getExcuse();

        if (event.getAttendanceStatus() != AttendanceStatus.ABSENT) {
            throw new FacialEventException("Solo se puede registrar excusa para inasistencias.", HttpStatus.BAD_REQUEST);
        }

        event.setExcuse(dto.getExcuse());
        event.setUpdatedBy(authenticatedUserId.toString());

        FacialEvent saved = facialEventRepository.saveAndFlush(event);
        attendanceNotifications.notifyExcuseOrAbsence(saved, newAbsentEvent, previousExcuse);
        if (Boolean.TRUE.equals(previousExcuse) && !Boolean.TRUE.equals(saved.getExcuse())) {
            attendanceNotifications.notifyExcuseRemoved(saved, authenticatedUserId);
        }
        return new FacialEventResponseDTO(saved);
    }

    private FacialEvent buildAbsentEvent(RecordEnvironment record, Apprentice apprentice, UUID authenticatedUserId) {
        FacialEvent event = new FacialEvent();
        event.setRecordEnvironment(record);
        event.setDevice(record.getDevice());
        event.setApprentice(apprentice);
        event.setEventDatetime(record.getEntryTime());
        event.setEventType(FacialEventType.ENTRY);
        event.setRecognitionResult("ABSENT");
        event.setAttendanceStatus(AttendanceStatus.ABSENT);
        event.setOrigin(FacialEventOrigin.PC);
        event.setExcuse(false);
        event.setCreatedBy(authenticatedUserId.toString());
        return event;
    }

    private FacialEventType resolveEventType(OffsetDateTime eventDatetime, RecordEnvironment record) {
        if (!eventDatetime.isBefore(record.getEntryTime()) && eventDatetime.isBefore(record.getExitTime())) {
            return FacialEventType.ENTRY;
        }
        if (!eventDatetime.isBefore(record.getExitTime()) && eventDatetime.isBefore(record.getShutdownTime())) {
            return FacialEventType.EXIT;
        }
        throw new FacialEventException(
                "El evento está fuera de las ventanas permitidas de entrada o salida.",
                HttpStatus.BAD_REQUEST);
    }

    private AttendanceStatus resolveAttendanceStatus(
            FacialEventType eventType,
            OffsetDateTime eventDatetime,
            RecordEnvironment record,
            Apprentice apprentice) {
        if (eventType == FacialEventType.ENTRY) {
            OffsetDateTime punctualLimit = record.getEntryTime().plusMinutes(record.getRegistrationMinutes());
            return eventDatetime.isAfter(punctualLimit)
                    ? AttendanceStatus.LATE
                    : AttendanceStatus.PUNCTUAL;
        }

        return facialEventRepository
                .findFirstByRecordEnvironment_IdRecordEnvironmentAndApprentice_IdApprenticeAndEventTypeAndDeletedAtIsNullOrderByEventDatetimeAsc(
                        record.getIdRecordEnvironment(),
                        apprentice.getIdApprentice(),
                        FacialEventType.ENTRY)
                .map(FacialEvent::getAttendanceStatus)
                .orElse(AttendanceStatus.ABSENT);
    }

    private void validateUniqueAttendanceEvent(
            UUID idRecordEnvironment,
            UUID idApprentice,
            FacialEventType eventType) {
        boolean alreadyRegistered = facialEventRepository
                .existsByRecordEnvironment_IdRecordEnvironmentAndApprentice_IdApprenticeAndEventTypeAndDeletedAtIsNull(
                        idRecordEnvironment,
                        idApprentice,
                        eventType);
        if (alreadyRegistered) {
            throw new FacialEventException("La asistencia ya fue registrada anteriormente", HttpStatus.CONFLICT);
        }
    }

    private FacialEventOrigin parseOrigin(String rawOrigin) {
        try {
            return FacialEventOrigin.valueOf(rawOrigin.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            throw new FacialEventException("El origen debe ser PC o MOBILE.", HttpStatus.BAD_REQUEST);
        }
    }

    private String extractVerificationServiceDetail(RestClientResponseException ex) {
        String body = ex.getResponseBodyAsString();
        if (body == null || body.isBlank()) {
            return ex.getMessage();
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(body);
            JsonNode detail = root.get("detail");
            if (detail != null && detail.isTextual()) {
                return detail.asText();
            }
        } catch (JsonProcessingException ignored) {
            // Se deja caer al cuerpo crudo para que el log conserve la respuesta original.
        }
        return body;
    }

    private String resolveVerificationServiceMessage(String detail) {
        if (detail == null || detail.isBlank()) {
            return "No fue posible verificar el rostro capturado.";
        }

        String normalized = detail.toLowerCase(Locale.ROOT);
        if (normalized.contains("no se detect")) {
            return "No se detectó un rostro válido en la imagen.";
        }
        if (normalized.contains("mas de un rostro") || normalized.contains("más de un rostro")) {
            return "La imagen contiene más de un rostro.";
        }
        if (normalized.contains("base64") || normalized.contains("leer la imagen") || normalized.contains("imagen es obligatoria")) {
            return "No fue posible leer la imagen capturada.";
        }
        if (normalized.contains("liveness") || normalized.contains("vida") || normalized.contains("frames")) {
            return "No se pudo comprobar que la captura sea de una persona en vivo.";
        }
        return "No fue posible verificar el rostro capturado.";
    }

    private String resolveLivenessFailureMessage(VerificationResponse verification) {
        if (verification == null || verification.livenessReason() == null || verification.livenessReason().isBlank()) {
            return "No se pudo comprobar que la captura sea de una persona en vivo.";
        }

        String reason = verification.livenessReason().trim();
        String normalized = reason.toLowerCase(Locale.ROOT);
        if (normalized.contains("misma persona")) {
            return "La secuencia no parece corresponder a la misma persona. Intenta moverte más suave.";
        }
        if (normalized.contains("puntos faciales")) {
            return "No se pudo medir bien el rostro. Centra la cara dentro del óvalo e intenta de nuevo.";
        }
        if (normalized.contains("movimiento facial")) {
            return "No se detectó suficiente movimiento. Haz el reto más despacio y un poco más marcado.";
        }
        if (normalized.contains("secuencia")) {
            return "La cámara no capturó suficientes imágenes para validar vida.";
        }
        return reason;
    }
    // Public compatibility methods used by correction and absence delivery flows.
    public String attendanceContext(FacialEvent event) {
        return attendanceNotifications.attendanceContext(event);
    }

    public String metadataFor(FacialEvent event, String reason) {
        return attendanceNotifications.metadataFor(event, reason);
    }
}
