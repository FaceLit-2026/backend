package com.FaceLit.backend.facial.service;

import com.FaceLit.backend.academic.model.academic.Instructor;
import com.FaceLit.backend.academic.model.enums.AcademicState;
import com.FaceLit.backend.academic.repository.InstructorRepository;
import com.FaceLit.backend.academic.repository.UserChipRepository;
import com.FaceLit.backend.facial.dto.response.AttendanceMatrixResponseDTO;
import com.FaceLit.backend.facial.exception.FacialEventException;
import com.FaceLit.backend.facial.repository.AttendanceMatrixRow;
import com.FaceLit.backend.facial.repository.AttendanceReadRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application service for authorized attendance reports. */
@Service
public class AttendanceMatrixService {
    private final AttendanceReadRepository attendanceReadRepository;
    private final UserChipRepository userChipRepository;
    private final InstructorRepository instructorRepository;
    private final FacialAccessPolicy accessPolicy;

    public AttendanceMatrixService(AttendanceReadRepository attendanceReadRepository,
            UserChipRepository userChipRepository, InstructorRepository instructorRepository,
            FacialAccessPolicy accessPolicy) {
        this.attendanceReadRepository = attendanceReadRepository;
        this.userChipRepository = userChipRepository;
        this.instructorRepository = instructorRepository;
        this.accessPolicy = accessPolicy;
    }

    @Transactional(readOnly = true)
    public AttendanceMatrixResponseDTO getAttendanceMatrix(
            UUID idChip,
            LocalDate dateFrom,
            LocalDate dateTo,
            UUID authenticatedUserId) {
        AttendanceDateRangePolicy.validate(dateFrom, dateTo);

        UUID ownUserId = null;
        UUID idInstructor = null;
        if (accessPolicy.hasRole(authenticatedUserId, "APPRENTICE")) {
            if (!userChipRepository.findByApprentice_User_IdUserAndState(authenticatedUserId, AcademicState.ACTIVE)
                    .map(link -> link.getChip().getIdChip().equals(idChip)).orElse(false)) {
                throw new FacialEventException("No puedes consultar esta ficha.", HttpStatus.FORBIDDEN);
            }
            ownUserId = authenticatedUserId;
        } else if (accessPolicy.hasRole(authenticatedUserId, "INSTRUCTOR")) {
            idInstructor = instructorRepository.findByUser_IdUser(authenticatedUserId)
                    .map(Instructor::getIdInstructor)
                    .orElseThrow(() -> new FacialEventException("Instructor no autorizado.", HttpStatus.FORBIDDEN));
            boolean assigned = attendanceReadRepository.isInstructorAssigned(idInstructor, idChip);
            if (!assigned) throw new FacialEventException("No puedes consultar esta ficha.", HttpStatus.FORBIDDEN);
        } else if (!accessPolicy.hasRole(authenticatedUserId, "COORDINATOR")) {
            throw new FacialEventException("No puedes consultar asistencias.", HttpStatus.FORBIDDEN);
        }

        List<AttendanceMatrixRow> rows = attendanceReadRepository.findAttendanceMatrixRows(
                idChip,
                idInstructor,
                ownUserId,
                dateFrom,
                dateTo);

        if (rows.isEmpty()) {
            return new AttendanceMatrixResponseDTO(idChip, null, null, null, List.of(), List.of());
        }

        AttendanceMatrixRow first = rows.get(0);
        Map<UUID, AttendanceMatrixResponseDTO.AttendanceMatrixSessionDTO> sessions = new LinkedHashMap<>();
        Map<UUID, LearnerMatrixBuilder> learners = new LinkedHashMap<>();

        for (AttendanceMatrixRow row : rows) {
            sessions.putIfAbsent(
                    row.recordEnvironmentId(),
                    new AttendanceMatrixResponseDTO.AttendanceMatrixSessionDTO(
                            row.recordEnvironmentId(),
                            row.sessionDate(),
                            row.environmentName(),
                            row.instructorName()));

            LearnerMatrixBuilder learner = learners.computeIfAbsent(row.idUser(), key -> new LearnerMatrixBuilder(
                    row.idUser(),
                    row.idApprentice(),
                    fullName(row.firstName(), row.lastName()),
                    row.documentNumber()));

            String status = row.attendanceStatus() == null || row.attendanceStatus().isBlank()
                    ? "absent"
                    : row.attendanceStatus().toLowerCase(Locale.ROOT);
            learner.days.add(new AttendanceMatrixResponseDTO.AttendanceMatrixDayDTO(
                    row.idFacialEvent(),
                    row.recordEnvironmentId(),
                    row.sessionDate(),
                    status,
                    row.entryTime(),
                    row.delayMinutes() == null ? 0 : row.delayMinutes(),
                    row.environmentName(),
                    row.instructorName(),
                    row.chipCode(),
                    row.programName(),
                    row.exitRegistered(),
                    row.excuse(),
                    row.sessionStartTime(),
                    row.sessionEndTime(),
                    row.exitTime(),
                    row.idEnvironment()));
        }

        return new AttendanceMatrixResponseDTO(
                first.idChip(),
                first.chipCode(),
                first.idProgram(),
                first.programName(),
                new ArrayList<>(sessions.values()),
                learners.values().stream().map(LearnerMatrixBuilder::toDto).toList());
    }

    private String fullName(String firstName, String lastName) {
        return ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).trim();
    }

    private static final class LearnerMatrixBuilder {
        private final UUID learnerId;
        private final UUID apprenticeId;
        private final String learnerName;
        private final String learnerDocument;
        private final List<AttendanceMatrixResponseDTO.AttendanceMatrixDayDTO> days = new ArrayList<>();

        private LearnerMatrixBuilder(UUID learnerId, UUID apprenticeId, String learnerName, String learnerDocument) {
            this.learnerId = learnerId;
            this.apprenticeId = apprenticeId;
            this.learnerName = learnerName;
            this.learnerDocument = learnerDocument;
        }

        private AttendanceMatrixResponseDTO.AttendanceMatrixLearnerDTO toDto() {
            return new AttendanceMatrixResponseDTO.AttendanceMatrixLearnerDTO(
                    learnerId,
                    apprenticeId,
                    learnerName,
                    learnerDocument,
                    days);
        }
    }

}
