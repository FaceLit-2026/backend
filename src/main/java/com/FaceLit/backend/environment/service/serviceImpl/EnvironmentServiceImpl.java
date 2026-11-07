package com.FaceLit.backend.environment.service.serviceImpl;

import com.FaceLit.backend.academic.dto.response.academic.ChipResponseDTO;
import com.FaceLit.backend.academic.dto.response.academic.InstructorResponseDTO;
import com.FaceLit.backend.academic.model.academic.Chip;
import com.FaceLit.backend.academic.model.academic.Instructor;
import com.FaceLit.backend.academic.model.academic.InstructorProgram;
import com.FaceLit.backend.academic.model.enums.AcademicState;
import com.FaceLit.backend.academic.model.enums.InstructorType;
import com.FaceLit.backend.academic.repository.ChipRepository;
import com.FaceLit.backend.academic.repository.InstructorProgramRepository;
import com.FaceLit.backend.academic.repository.InstructorRepository;
import com.FaceLit.backend.academic.repository.UserChipRepository;
import com.FaceLit.backend.auth.model.enums.AccountStatus;
import com.FaceLit.backend.auth.service.roleandpermission.LoginService;
import com.FaceLit.backend.environment.dto.request.EnvironmentRequestDTO;
import com.FaceLit.backend.environment.dto.request.RecordEnvironmentRequestDTO;
import com.FaceLit.backend.environment.dto.request.EnvironmentAssignmentRequestDTO;
import com.FaceLit.backend.environment.dto.response.EnvironmentResponseDTO;
import com.FaceLit.backend.environment.dto.response.RecordEnvironmentResponseDTO;
import com.FaceLit.backend.environment.dto.response.EnvironmentAssignmentResponseDTO;
import com.FaceLit.backend.environment.exception.EnvironmentException;
import com.FaceLit.backend.environment.model.Environment;
import com.FaceLit.backend.environment.model.ChipEnvironment;
import com.FaceLit.backend.environment.model.RecordEnvironment;
import com.FaceLit.backend.environment.model.enums.EnvironmentState;
import com.FaceLit.backend.environment.repository.EnvironmentRepository;
import com.FaceLit.backend.environment.repository.ChipEnvironmentRepository;
import com.FaceLit.backend.environment.repository.RecordEnvironmentRepository;
import com.FaceLit.backend.environment.service.EnvironmentService;
import com.FaceLit.backend.environment.service.EnvironmentSessionNotifier.SessionSnapshot;
import com.FaceLit.backend.environment.service.EnvironmentSessionNotifier;
import com.FaceLit.backend.facial.model.Device;
import com.FaceLit.backend.facial.repository.DeviceRepository;
import com.FaceLit.backend.facial.repository.FacialEventRepository;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EnvironmentServiceImpl implements EnvironmentService {
    private final EnvironmentRepository environmentRepository;
    private final RecordEnvironmentRepository recordEnvironmentRepository;
    private final ChipEnvironmentRepository chipEnvironmentRepository;
    private final InstructorRepository instructorRepository;
    private final InstructorProgramRepository instructorProgramRepository;
    private final ChipRepository chipRepository;
    private final UserChipRepository userChipRepository;
    private final DeviceRepository deviceRepository;
    private final LoginService loginService;
    private final EnvironmentSessionNotifier sessionNotifier;
    private final FacialEventRepository attendance;

    public EnvironmentServiceImpl(
            EnvironmentRepository environmentRepository,
            RecordEnvironmentRepository recordEnvironmentRepository,
            ChipEnvironmentRepository chipEnvironmentRepository,
            InstructorRepository instructorRepository,
            InstructorProgramRepository instructorProgramRepository,
            ChipRepository chipRepository,
            UserChipRepository userChipRepository,
            DeviceRepository deviceRepository,
            LoginService loginService,
            EnvironmentSessionNotifier sessionNotifier,
            FacialEventRepository attendance) {
        this.environmentRepository = environmentRepository;
        this.recordEnvironmentRepository = recordEnvironmentRepository;
        this.chipEnvironmentRepository = chipEnvironmentRepository;
        this.instructorRepository = instructorRepository;
        this.instructorProgramRepository = instructorProgramRepository;
        this.chipRepository = chipRepository;
        this.userChipRepository = userChipRepository;
        this.deviceRepository = deviceRepository;
        this.loginService = loginService;
        this.sessionNotifier = sessionNotifier;
        this.attendance = attendance;
    }

    @Override
    @Transactional(readOnly = true)
    public List<EnvironmentResponseDTO> searchEnvironments(String search) {
        return searchEnvironments(search, false);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EnvironmentResponseDTO> findEnvironmentsForChip(UUID idChip) {
        return chipEnvironmentRepository.findByChip_IdChipAndStateAndDeletedAtIsNull(idChip, AcademicState.ACTIVE)
                .stream().map(ChipEnvironment::getEnvironment)
                .filter(environment -> environment.getState() == EnvironmentState.ACTIVE && environment.getDeletedAt() == null)
                .distinct().sorted(Comparator.comparing(Environment::getEnvironmentName))
                .map(EnvironmentResponseDTO::new).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EnvironmentResponseDTO> searchEnvironments(String search, boolean includeInactive) {
        String normalizedSearch = search == null ? "" : search.trim();
        List<Environment> environments;
        if (includeInactive) {
            environments = environmentRepository.findAllByOrderByEnvironmentNameAsc().stream()
                    .filter(item -> normalizedSearch.isBlank()
                            || item.getEnvironmentName().toLowerCase().contains(normalizedSearch.toLowerCase()))
                    .toList();
        } else {
            environments = normalizedSearch.isBlank()
                    ? environmentRepository.findByStateOrderByEnvironmentNameAsc(EnvironmentState.ACTIVE)
                    : environmentRepository.searchActive(normalizedSearch, EnvironmentState.ACTIVE);
        }
        return environments.stream().map(EnvironmentResponseDTO::new).toList();
    }

    @Override
    @Transactional
    public EnvironmentResponseDTO getOrCreateEnvironment(EnvironmentRequestDTO dto, UUID authenticatedUserId) {
        String environmentName = normalizeName(dto.getEnvironmentName());
        if (environmentName.isBlank()) {
            throw new EnvironmentException("El nombre del ambiente es obligatorio.", HttpStatus.BAD_REQUEST);
        }

        return environmentRepository.findByNormalizedName(environmentName)
                .map(environment -> {
                    if (environment.getState() != EnvironmentState.ACTIVE) {
                        environment.setState(EnvironmentState.ACTIVE);
                        environment.setDeletedAt(null);
                        environment.setDeletedBy(null);
                        environment.setUpdatedBy(authenticatedUserId.toString());
                        if (dto.getCapacity() != null) {
                            environment.setCapacity(dto.getCapacity());
                        }
                        return new EnvironmentResponseDTO(environmentRepository.save(environment));
                    }
                    return new EnvironmentResponseDTO(environment);
                })
                .orElseGet(() -> {
                    Environment environment = new Environment();
                    environment.setEnvironmentName(environmentName);
                    environment.setCapacity(dto.getCapacity());
                    environment.setState(EnvironmentState.ACTIVE);
                    environment.setCreatedBy(authenticatedUserId.toString());
                    return new EnvironmentResponseDTO(environmentRepository.save(environment));
                });
    }

    @Override
    @Transactional
    public EnvironmentResponseDTO updateEnvironment(UUID idEnvironment, EnvironmentRequestDTO dto, UUID authenticatedUserId) {
        Environment environment = environmentRepository.findById(idEnvironment)
                .orElseThrow(() -> new EnvironmentException("Ambiente no encontrado.", HttpStatus.NOT_FOUND));
        if (environment.getDeletedAt() != null && dto.getState() != EnvironmentState.ACTIVE) {
            throw new EnvironmentException("Ambiente no encontrado.", HttpStatus.NOT_FOUND);
        }
        String name = dto.getEnvironmentName() == null ? environment.getEnvironmentName() : normalizeName(dto.getEnvironmentName());
        if (name.isBlank()) {
            throw new EnvironmentException("El nombre del ambiente es obligatorio.", HttpStatus.BAD_REQUEST);
        }
        if (dto.getCapacity() != null && dto.getCapacity() <= 0) {
            throw new EnvironmentException("La capacidad debe ser mayor que cero.", HttpStatus.BAD_REQUEST);
        }
        environmentRepository.findByNormalizedName(name).ifPresent(existing -> {
            if (!existing.getIdEnvironment().equals(idEnvironment)) {
                throw new EnvironmentException("Ya existe un ambiente con ese nombre.", HttpStatus.CONFLICT);
            }
        });
        environment.setEnvironmentName(name);
        if (dto.getCapacity() != null) environment.setCapacity(dto.getCapacity());
        if (dto.getState() != null) {
            environment.setState(dto.getState());
            if (dto.getState() == EnvironmentState.ACTIVE) {
                environment.setDeletedAt(null);
                environment.setDeletedBy(null);
            }
        }
        environment.setUpdatedBy(authenticatedUserId.toString());
        return new EnvironmentResponseDTO(environmentRepository.save(environment));
    }

    @Override
    @Transactional
    public void deactivateEnvironment(UUID idEnvironment, UUID authenticatedUserId) {
        Environment environment = environmentRepository.findById(idEnvironment)
                .orElseThrow(() -> new EnvironmentException("Ambiente no encontrado.", HttpStatus.NOT_FOUND));
        if (environment.getDeletedAt() == null) {
            environment.setState(EnvironmentState.INACTIVE);
            environment.setDeletedAt(OffsetDateTime.now());
            environment.setDeletedBy(authenticatedUserId.toString());
            environment.setUpdatedBy(authenticatedUserId.toString());
            environmentRepository.save(environment);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<EnvironmentAssignmentResponseDTO> findAssignments(UUID idEnvironment) {
        return chipEnvironmentRepository
                .findByEnvironment_IdEnvironmentAndStateAndDeletedAtIsNull(idEnvironment, AcademicState.ACTIVE)
                .stream().map(EnvironmentAssignmentResponseDTO::new).toList();
    }

    @Override
    @Transactional
    public EnvironmentAssignmentResponseDTO assignFicha(UUID idEnvironment, EnvironmentAssignmentRequestDTO dto,
            UUID authenticatedUserId) {
        Environment environment = environmentRepository.findById(idEnvironment)
                .orElseThrow(() -> new EnvironmentException("Ambiente no encontrado.", HttpStatus.NOT_FOUND));
        if (environment.getState() != EnvironmentState.ACTIVE || environment.getDeletedAt() != null) {
            throw new EnvironmentException("El ambiente no está activo.", HttpStatus.CONFLICT);
        }
        Chip chip = chipRepository.findById(dto.getIdChip())
                .orElseThrow(() -> new EnvironmentException("La ficha no existe.", HttpStatus.NOT_FOUND));
        if (chip.getState() != AcademicState.ACTIVE || chip.getDeletedAt() != null) {
            throw new EnvironmentException("La ficha no está activa.", HttpStatus.CONFLICT);
        }
        if (chipEnvironmentRepository.findByEnvironment_IdEnvironmentAndChip_IdChipAndStateAndDeletedAtIsNull(
                idEnvironment, chip.getIdChip(), AcademicState.ACTIVE).isPresent()) {
            throw new EnvironmentException("La ficha ya está asignada a este ambiente.", HttpStatus.CONFLICT);
        }
        if (environment.getCapacity() != null && chipEnvironmentRepository
                .findByEnvironment_IdEnvironmentAndStateAndDeletedAtIsNull(idEnvironment, AcademicState.ACTIVE).size()
                >= environment.getCapacity()) {
            throw new EnvironmentException("La capacidad del ambiente está completa.", HttpStatus.CONFLICT);
        }
        ChipEnvironment assignment = new ChipEnvironment();
        assignment.setEnvironment(environment);
        assignment.setChip(chip);
        assignment.setState(AcademicState.ACTIVE);
        assignment.setCreatedBy(authenticatedUserId.toString());
        return new EnvironmentAssignmentResponseDTO(chipEnvironmentRepository.save(assignment));
    }

    @Override
    @Transactional
    public void unassignFicha(UUID idEnvironment, UUID idChip, UUID authenticatedUserId) {
        ChipEnvironment assignment = chipEnvironmentRepository
                .findByEnvironment_IdEnvironmentAndChip_IdChipAndStateAndDeletedAtIsNull(
                        idEnvironment, idChip, AcademicState.ACTIVE)
                .orElseThrow(() -> new EnvironmentException("La asignación no existe.", HttpStatus.NOT_FOUND));
        assignment.setState(AcademicState.INACTIVE);
        assignment.setDeletedAt(OffsetDateTime.now());
        assignment.setDeletedBy(authenticatedUserId.toString());
        assignment.setUpdatedBy(authenticatedUserId.toString());
        chipEnvironmentRepository.save(assignment);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InstructorResponseDTO> findInstructorsForSession(UUID authenticatedUserId) {
        Instructor authenticatedInstructor = instructorRepository.findByUser_IdUser(authenticatedUserId)
                .orElseThrow(() -> new EnvironmentException("Instructor no encontrado.", HttpStatus.NOT_FOUND));

        List<Instructor> candidates = authenticatedInstructor.getInstructorType() == InstructorType.TRANSVERSAL
                ? instructorRepository.findAll()
                : findEligibleForSpecificInstructor(authenticatedInstructor);

        return candidates.stream()
                .filter(instructor -> instructor.getDeletedAt() == null && instructor.getUser().getDeletedAt() == null)
                .filter(instructor -> instructor.getUser().getAccountStatus() == AccountStatus.ACTIVE)
                .sorted(Comparator.comparing(instructor -> sessionNotifier.fullName(instructor).toLowerCase()))
                .map(this::toInstructorResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChipResponseDTO> findChipsForInstructor(UUID idInstructor, UUID authenticatedUserId) {
        Instructor instructor = instructorRepository.findById(idInstructor)
                .orElseThrow(() -> new EnvironmentException("Instructor no encontrado.", HttpStatus.NOT_FOUND));

        List<Chip> chips = instructor.getInstructorType() == InstructorType.TRANSVERSAL
                ? chipRepository.findByState(AcademicState.ACTIVE)
                : chipRepository.findByProgram_IdProgramIn(programIdsForInstructor(instructor)).stream()
                        .filter(chip -> chip.getState() == AcademicState.ACTIVE)
                        .toList();

        Instructor actor = findAuthenticatedInstructor(authenticatedUserId);
        return chips.stream()
                .filter(chip -> canUseChip(actor, chip) && canUseChip(instructor, chip))
                .sorted(Comparator.comparing(Chip::getChipCode))
                .map(ChipResponseDTO::new)
                .toList();
    }

    @Override
    @Transactional
    public RecordEnvironmentResponseDTO createSession(RecordEnvironmentRequestDTO dto, UUID authenticatedUserId) {
        validateTimes(dto);

        Environment environment = findActiveEnvironment(dto.getIdEnvironment());
        Instructor scheduledInstructor = findInstructor(dto.getIdInstructorInCharge());
        Instructor inChargeInstructor = resolveInstructorInCharge(scheduledInstructor, authenticatedUserId);
        Chip chip = findActiveChip(dto.getIdChip());
        validateSessionInstructors(scheduledInstructor, inChargeInstructor, chip);
        validateChipHasRegisteredFaces(chip);
        Device device = findOrCreateDevice(dto.getDeviceCode(), environment, authenticatedUserId);

        RecordEnvironment record = new RecordEnvironment();
        record.setEnvironment(environment);
        record.setDevice(device);
        record.setInstructorScheduled(scheduledInstructor);
        record.setInstructorInCharge(inChargeInstructor);
        record.setChip(chip);
        record.setEntryTime(dto.getEntryTime());
        record.setRegistrationMinutes(dto.getRegistrationMinutes());
        record.setExitTime(dto.getExitTime());
        record.setShutdownTime(dto.getShutdownTime());
        record.setExitReminderSent(false);
        record.setActive(true);
        record.setCreatedBy(authenticatedUserId.toString());

        record = recordEnvironmentRepository.save(record);
        if (record.getInstructorInCharge() == null) sessionNotifier.notifySessionAssigned(record);
        else sessionNotifier.notifySessionSubstitution(record, authenticatedUserId);
        return new RecordEnvironmentResponseDTO(record);
    }

    @Override
    @Transactional
    public Optional<RecordEnvironmentResponseDTO> findCurrentActiveSession(UUID authenticatedUserId) {
        recordEnvironmentRepository.deactivateExpiredSessions(OffsetDateTime.now());
        OffsetDateTime now = OffsetDateTime.now();
        return recordEnvironmentRepository.findCurrentActiveSessionsByInstructor(
                        authenticatedUserId, org.springframework.data.domain.PageRequest.of(0, 1)).stream().findFirst()
                .or(() -> recordEnvironmentRepository
                        .findFirstByCreatedByAndActiveTrueAndDeletedAtIsNullAndShutdownTimeAfterOrderByEntryTimeDesc(
                                authenticatedUserId.toString(), now))
                .map(RecordEnvironmentResponseDTO::new);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean verifySessionExitPassword(UUID idRecordEnvironment, UUID authenticatedUserId,
            String credentialOwner, String password) {
        RecordEnvironment record = recordEnvironmentRepository.findById(idRecordEnvironment).orElse(null);
        if (record == null || !Boolean.TRUE.equals(record.getActive())
                || record.getShutdownTime() == null
                || !record.getShutdownTime().isAfter(OffsetDateTime.now())) {
            return false;
        }

        UUID responsibleUserId = record.getInstructorScheduled().getUser().getIdUser();
        UUID replacementUserId = record.getInstructorInCharge() == null
                ? null
                : record.getInstructorInCharge().getUser().getIdUser();
        if (!authenticatedUserId.equals(responsibleUserId)
                && !authenticatedUserId.equals(replacementUserId)) {
            return false;
        }

        UUID credentialUserId;
        if ("REPLACEMENT".equalsIgnoreCase(credentialOwner)) {
            if (replacementUserId == null) return false;
            credentialUserId = replacementUserId;
        } else if ("RESPONSIBLE".equalsIgnoreCase(credentialOwner)) {
            credentialUserId = responsibleUserId;
        } else {
            return false;
        }
        return loginService.verifyPassword(credentialUserId, password);
    }

    @Scheduled(fixedDelay = 30_000)
    @Transactional
    public void deactivateExpiredSessions() {
        recordEnvironmentRepository.deactivateExpiredSessions(OffsetDateTime.now());
    }

    @Override
    @Transactional
    public RecordEnvironmentResponseDTO updateSession(UUID idRecordEnvironment, RecordEnvironmentRequestDTO dto, UUID authenticatedUserId) {
        validateTimes(dto);
        RecordEnvironment record = recordEnvironmentRepository.findById(idRecordEnvironment)
                .orElseThrow(() -> new EnvironmentException("Sesión no encontrada.", HttpStatus.NOT_FOUND));
        boolean belongs = record.getInstructorScheduled().getUser().getIdUser().equals(authenticatedUserId)
                || record.getInstructorInCharge() != null
                && record.getInstructorInCharge().getUser().getIdUser().equals(authenticatedUserId);
        if (!belongs || record.getDeletedAt() != null) {
            throw new EnvironmentException("No puedes modificar esta sesión.", HttpStatus.FORBIDDEN);
        }

        if (!Boolean.TRUE.equals(record.getActive()) || !record.getShutdownTime().isAfter(OffsetDateTime.now())) {
            throw new EnvironmentException("La sesión ya terminó.", HttpStatus.CONFLICT);
        }
        SessionSnapshot previous = SessionSnapshot.from(record);
        // Events refer to this session: changing its context would rewrite their history.
        if (attendance.existsByRecordEnvironment_IdRecordEnvironment(idRecordEnvironment)) {
            throw new EnvironmentException("La sesión ya tiene asistencias; conserva su configuración y crea otra sesión.", HttpStatus.CONFLICT);
        }
        if (previous.exitTime() == null || !previous.exitTime().isEqual(dto.getExitTime())) record.setExitReminderSent(false);

        Environment environment = findActiveEnvironment(dto.getIdEnvironment());
        record.setEnvironment(environment);
        record.setDevice(findOrCreateDevice(dto.getDeviceCode(), environment, authenticatedUserId));
        Instructor scheduledInstructor = findInstructor(dto.getIdInstructorInCharge());
        record.setInstructorScheduled(scheduledInstructor);
        record.setInstructorInCharge(resolveInstructorInCharge(scheduledInstructor, authenticatedUserId));
        Chip chip = findActiveChip(dto.getIdChip());
        validateSessionInstructors(scheduledInstructor, record.getInstructorInCharge(), chip);
        validateChipHasRegisteredFaces(chip);
        record.setChip(chip);
        record.setEntryTime(dto.getEntryTime());
        record.setRegistrationMinutes(dto.getRegistrationMinutes());
        record.setExitTime(dto.getExitTime());
        record.setShutdownTime(dto.getShutdownTime());
        record.setUpdatedBy(authenticatedUserId.toString());

        RecordEnvironment saved = recordEnvironmentRepository.save(record);
        sessionNotifier.notifySessionUpdated(saved, previous, authenticatedUserId);
        return new RecordEnvironmentResponseDTO(saved);
    }

    private List<Instructor> findEligibleForSpecificInstructor(Instructor instructor) {
        List<UUID> programIds = programIdsForInstructor(instructor);
        Map<UUID, Instructor> unique = new LinkedHashMap<>();
        instructorRepository.findAll().stream()
                .filter(candidate -> candidate.getInstructorType() == InstructorType.TRANSVERSAL
                        || instructorProgramRepository.findByInstructor_IdInstructor(candidate.getIdInstructor()).stream()
                                .map(ip -> ip.getProgram().getIdProgram())
                                .anyMatch(programIds::contains))
                .forEach(candidate -> unique.put(candidate.getIdInstructor(), candidate));
        return unique.values().stream().toList();
    }

    private List<UUID> programIdsForInstructor(Instructor instructor) {
        return instructorProgramRepository.findByInstructor_IdInstructor(instructor.getIdInstructor()).stream()
                .filter(link -> link.getDeletedAt() == null && link.getProgram().getDeletedAt() == null)
                .map(InstructorProgram::getProgram)
                .map(program -> program.getIdProgram())
                .distinct()
                .toList();
    }

    private InstructorResponseDTO toInstructorResponse(Instructor instructor) {
        return new InstructorResponseDTO(instructor,
                instructorProgramRepository.findByInstructor_IdInstructor(instructor.getIdInstructor()));
    }

    private Environment findActiveEnvironment(UUID idEnvironment) {
        Environment environment = environmentRepository.findById(idEnvironment)
                .orElseThrow(() -> new EnvironmentException("Ambiente no encontrado.", HttpStatus.NOT_FOUND));
        if (environment.getState() != EnvironmentState.ACTIVE || environment.getDeletedAt() != null) {
            throw new EnvironmentException("Ambiente no encontrado.", HttpStatus.NOT_FOUND);
        }
        return environment;
    }

    private Instructor findInstructor(UUID idInstructor) {
        return instructorRepository.findById(idInstructor)
                .orElseThrow(() -> new EnvironmentException("Instructor no encontrado.", HttpStatus.NOT_FOUND));
    }

    private Instructor findAuthenticatedInstructor(UUID authenticatedUserId) {
        return instructorRepository.findByUser_IdUser(authenticatedUserId)
                .orElseThrow(() -> new EnvironmentException("Instructor no encontrado.", HttpStatus.NOT_FOUND));
    }

    private Instructor resolveInstructorInCharge(Instructor scheduledInstructor, UUID authenticatedUserId) {
        Instructor authenticatedInstructor = findAuthenticatedInstructor(authenticatedUserId);
        return authenticatedInstructor.getIdInstructor().equals(scheduledInstructor.getIdInstructor())
                ? null : authenticatedInstructor;
    }

    private boolean canUseChip(Instructor instructor, Chip chip) {
        return instructor.getDeletedAt() == null && instructor.getUser().getDeletedAt() == null
                && instructor.getUser().getAccountStatus() == AccountStatus.ACTIVE
                && chip.getDeletedAt() == null && chip.getProgram().getDeletedAt() == null
                && chip.getProgram().getState() == AcademicState.ACTIVE
                && (instructor.getInstructorType() == InstructorType.TRANSVERSAL
                    || programIdsForInstructor(instructor).contains(chip.getProgram().getIdProgram()));
    }

    private void validateSessionInstructors(Instructor scheduled, Instructor replacement, Chip chip) {
        if (!canUseChip(scheduled, chip) || replacement != null && !canUseChip(replacement, chip)) {
            throw new EnvironmentException("El instructor no puede configurar una sesión para esta ficha.", HttpStatus.FORBIDDEN);
        }
    }

    private Chip findActiveChip(UUID idChip) {
        Chip chip = chipRepository.findById(idChip)
                .orElseThrow(() -> new EnvironmentException("La ficha no está activa.", HttpStatus.BAD_REQUEST));
        if (chip.getState() != AcademicState.ACTIVE || chip.getDeletedAt() != null) {
            throw new EnvironmentException("La ficha no está activa.", HttpStatus.BAD_REQUEST);
        }
        return chip;
    }

    private void validateChipHasRegisteredFaces(Chip chip) {
        if (userChipRepository.countActiveRegisteredFacesByChip(chip.getIdChip()) == 0) {
            throw new EnvironmentException(
                    "Esta ficha aún no tiene rostros registrados",
                    HttpStatus.BAD_REQUEST);
        }
    }

    private Device findOrCreateDevice(String rawDeviceCode, Environment environment, UUID authenticatedUserId) {
        String deviceCode = rawDeviceCode == null ? "" : rawDeviceCode.trim();
        if (deviceCode.isBlank() || deviceCode.length() > 50) {
            throw new EnvironmentException("El dispositivo no es valido.", HttpStatus.BAD_REQUEST);
        }
        return deviceRepository.findByDeviceCode(deviceCode)
                .map(device -> {
                    if (!device.getEnvironment().getIdEnvironment().equals(environment.getIdEnvironment())) {
                        device.setEnvironment(environment);
                        device.setLocation(environment.getEnvironmentName());
                        device.setUpdatedBy(authenticatedUserId.toString());
                    }
                    if (!"ACTIVE".equals(device.getStatus())) {
                        device.setStatus("ACTIVE");
                        device.setUpdatedBy(authenticatedUserId.toString());
                    }
                    return deviceRepository.save(device);
                })
                .orElseGet(() -> {
                    Device device = new Device();
                    device.setEnvironment(environment);
                    device.setDeviceCode(deviceCode);
                    device.setLocation(environment.getEnvironmentName());
                    device.setStatus("ACTIVE");
                    device.setCreatedBy(authenticatedUserId.toString());
                    return deviceRepository.save(device);
                });
    }

    private void validateTimes(RecordEnvironmentRequestDTO dto) {
        if (dto.getEntryTime() == null) {
            throw new EnvironmentException("La hora de entrada es obligatoria.", HttpStatus.BAD_REQUEST);
        }
        if (dto.getExitTime() == null) {
            throw new EnvironmentException("La hora de salida es obligatoria.", HttpStatus.BAD_REQUEST);
        }
        if (dto.getShutdownTime() == null) {
            throw new EnvironmentException("La hora de apagado es obligatoria.", HttpStatus.BAD_REQUEST);
        }
        if (!dto.getExitTime().isAfter(dto.getEntryTime())) {
            throw new EnvironmentException("La hora de salida debe ser posterior a la hora de entrada.", HttpStatus.BAD_REQUEST);
        }
        if (dto.getRegistrationMinutes() == null || dto.getRegistrationMinutes() <= 0
                || !dto.getExitTime().isAfter(dto.getEntryTime().plusMinutes(dto.getRegistrationMinutes()))) {
            throw new EnvironmentException("La hora de salida debe ser posterior al tiempo de registro.", HttpStatus.BAD_REQUEST);
        }
        if (!dto.getShutdownTime().isAfter(dto.getExitTime())) {
            throw new EnvironmentException("La hora de apagado debe ser posterior a la hora de salida.", HttpStatus.BAD_REQUEST);
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (!dto.getExitTime().isAfter(now)) {
            throw new EnvironmentException("No se puede guardar una sección con la hora de salida en el pasado.", HttpStatus.BAD_REQUEST);
        }
        if (!dto.getShutdownTime().isAfter(now)) {
            throw new EnvironmentException("No se puede guardar una sección con la hora de apagado en el pasado.", HttpStatus.BAD_REQUEST);
        }
    }

    private String normalizeName(String environmentName) {
        return environmentName == null ? "" : environmentName.trim().replaceAll("\\s+", " ");
    }

}
