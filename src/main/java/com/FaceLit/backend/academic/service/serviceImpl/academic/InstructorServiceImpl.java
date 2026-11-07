package com.FaceLit.backend.academic.service.serviceImpl.academic;

import com.FaceLit.backend.academic.dto.request.academic.InstructorRequestDTO;
import com.FaceLit.backend.academic.dto.response.academic.InstructorResponseDTO;
import com.FaceLit.backend.academic.exception.AcademicException;
import com.FaceLit.backend.academic.model.academic.ChangeHistory;
import com.FaceLit.backend.academic.model.academic.Instructor;
import com.FaceLit.backend.academic.model.academic.InstructorProgram;
import com.FaceLit.backend.academic.model.academic.Program;
import com.FaceLit.backend.academic.model.enums.ChangeAction;
import com.FaceLit.backend.academic.model.enums.InstructorType;
import com.FaceLit.backend.academic.repository.ChangeHistoryRepository;
import com.FaceLit.backend.academic.repository.InstructorProgramRepository;
import com.FaceLit.backend.academic.repository.InstructorRepository;
import com.FaceLit.backend.academic.repository.ProgramRepository;
import com.FaceLit.backend.academic.service.academic.InstructorService;
import com.FaceLit.backend.academic.service.notification.InstructorNotificationService;
import com.FaceLit.backend.auth.dto.request.roleandpermission.AssignRoleRequestDTO;
import com.FaceLit.backend.auth.model.enums.AccountStatus;
import com.FaceLit.backend.auth.model.enums.CredentialStatus;
import com.FaceLit.backend.auth.model.enums.RoleName;
import com.FaceLit.backend.auth.model.security.Credential;
import com.FaceLit.backend.auth.model.security.User;
import com.FaceLit.backend.auth.repository.security.CredentialRepository;
import com.FaceLit.backend.auth.repository.security.UserRepository;
import com.FaceLit.backend.auth.service.roleandpermission.AdminRoleService;
import com.FaceLit.backend.auth.service.security.InitialCredentialService;
import com.FaceLit.backend.environment.repository.RecordEnvironmentRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InstructorServiceImpl implements InstructorService {
    private final InstructorRepository instructorRepository;
    private final InstructorProgramRepository instructorProgramRepository;
    private final ProgramRepository programRepository;
    private final UserRepository userRepository;
    private final CredentialRepository credentialRepository;
    private final ChangeHistoryRepository changeHistoryRepository;
    private final RecordEnvironmentRepository recordEnvironmentRepository;
    private final InitialCredentialService initialCredentialService;
    private final AdminRoleService adminRoleService;
    private final com.FaceLit.backend.auth.service.serviceImpl.security.SessionSecurityService sessions;
    private final com.FaceLit.backend.notification.service.ProfileChangeNotificationService profileChanges;
    private final InstructorNotificationService instructorNotifications;

    public InstructorServiceImpl(
            InstructorRepository instructorRepository,
            InstructorProgramRepository instructorProgramRepository,
            ProgramRepository programRepository,
            UserRepository userRepository,
            CredentialRepository credentialRepository,
            ChangeHistoryRepository changeHistoryRepository,
            RecordEnvironmentRepository recordEnvironmentRepository,
            InitialCredentialService initialCredentialService,
            AdminRoleService adminRoleService,
            com.FaceLit.backend.auth.service.serviceImpl.security.SessionSecurityService sessions,
            com.FaceLit.backend.notification.service.ProfileChangeNotificationService profileChanges,
            InstructorNotificationService instructorNotifications) {
        this.instructorRepository = instructorRepository;
        this.instructorProgramRepository = instructorProgramRepository;
        this.programRepository = programRepository;
        this.userRepository = userRepository;
        this.credentialRepository = credentialRepository;
        this.changeHistoryRepository = changeHistoryRepository;
        this.recordEnvironmentRepository = recordEnvironmentRepository;
        this.initialCredentialService = initialCredentialService;
        this.adminRoleService = adminRoleService;
        this.sessions = sessions;
        this.profileChanges = profileChanges;
        this.instructorNotifications = instructorNotifications;
    }

    @Override
    @Transactional
    public InstructorResponseDTO create(InstructorRequestDTO dto) {
        if (dto.getIdUser() != null) {
            User existingUser = userRepository.findById(dto.getIdUser())
                    .orElseThrow(() -> new AcademicException("Usuario no encontrado.", HttpStatus.NOT_FOUND));
            return createInstructorForUser(existingUser, dto.getInstructorType(), dto.getProgramIds(), null);
        }

        String document = normalizeDocument(dto.getDocumento());
        String email = normalizeEmail(dto.getCorreo());
        if (document.isBlank()) {
            throw new AcademicException("El documento es obligatorio.", HttpStatus.BAD_REQUEST);
        }
        if (email.isBlank()) {
            throw new AcademicException("El correo es obligatorio.", HttpStatus.BAD_REQUEST);
        }
        if (!document.matches("\\d{6,15}")) {
            throw new AcademicException("El documento solo puede contener letras y números, y debe tener entre 6 y 30 caracteres.", HttpStatus.BAD_REQUEST);
        }
        if (userRepository.existsByDocumentNumber(document)) {
            throw new AcademicException("Este número de documento ya está registrado.", HttpStatus.CONFLICT);
        }
        if (credentialRepository.existsByEmailIgnoreCase(email)) {
            throw new AcademicException("Ya existe un usuario con ese correo electrónico.", HttpStatus.CONFLICT);
        }

        User newUser = new User();
        newUser.setDocumentNumber(document);
        newUser.setFirstName(dto.getNombre().trim());
        newUser.setLastName(dto.getApellido().trim());
        newUser.setAccountStatus(AccountStatus.ACTIVE);
        newUser = userRepository.saveAndFlush(newUser);

        String password = initialCredentialService.generateInitialPassword();
        Credential credential = new Credential();
        credential.setUser(newUser);
        credential.setEmail(email);
        initialCredentialService.encodeGeneratedPassword(credential, password);
        credential.setCredentialStatus(CredentialStatus.ACTIVE);
        credential.setFailedAttempts(0);
        credentialRepository.saveAndFlush(credential);

        InstructorResponseDTO response = createInstructorForUser(newUser, dto.getInstructorType(), dto.getProgramIds(), null);
        initialCredentialService.sendWelcomeAfterCommit(
                newUser,
                credential,
                RoleName.INSTRUCTOR,
                password,
                null,
                String.join(", ", response.getProgramNames()));
        return response;
    }

    @Override
    @Transactional
    public InstructorResponseDTO update(UUID idInstructor, InstructorRequestDTO dto) {
        Instructor instructor = instructorRepository.findById(idInstructor)
                .orElseThrow(() -> new AcademicException("Instructor no encontrado.", HttpStatus.NOT_FOUND));

        User user = instructor.getUser();
        Credential credential = user.getCredential();
        String document = normalizeDocument(dto.getDocumento());
        String email = normalizeEmail(dto.getCorreo());
        if (document.isBlank()) {
            throw new AcademicException("El documento es obligatorio.", HttpStatus.BAD_REQUEST);
        }
        if (email.isBlank()) {
            throw new AcademicException("El correo es obligatorio.", HttpStatus.BAD_REQUEST);
        }
        if (!document.matches("\\d{6,15}")) {
            throw new AcademicException("El documento debe contener solo digitos y tener entre 6 y 15 caracteres.", HttpStatus.BAD_REQUEST);
        }

        userRepository.findByDocumentNumber(document)
                .filter(existing -> !existing.getIdUser().equals(user.getIdUser()))
                .ifPresent(existing -> {
                    throw new AcademicException("Este numero de documento ya esta registrado.", HttpStatus.CONFLICT);
                });

        credentialRepository.findByEmailIgnoreCase(email)
                .filter(existing -> !existing.getUser().getIdUser().equals(user.getIdUser()))
                .ifPresent(existing -> {
                    throw new AcademicException("Ya existe un usuario con ese correo electronico.", HttpStatus.CONFLICT);
                });

        String oldDocument = user.getDocumentNumber();
        String oldFirstName = user.getFirstName();
        String oldLastName = user.getLastName();
        String oldEmail = credential != null ? credential.getEmail() : "";
        String oldProfile = oldDocument + " / " + oldFirstName + " " + oldLastName + " / " + oldEmail;
        InstructorType oldType = instructor.getInstructorType();

        List<InstructorProgram> currentPrograms = instructorProgramRepository.findByInstructor_IdInstructor(idInstructor);
        List<UUID> oldProgramIds = currentPrograms.stream()
                .map(ip -> ip.getProgram().getIdProgram())
                .toList();
        List<String> oldProgramNames = currentPrograms.stream()
                .map(ip -> ip.getProgram().getProgramName() + " (" + ip.getProgram().getProgramCode() + ")")
                .toList();

        InstructorType newType = dto.getInstructorType();
        user.setDocumentNumber(document);
        user.setFirstName(dto.getNombre().trim());
        user.setLastName(dto.getApellido().trim());
        if (credential == null) {
            throw new AcademicException("El instructor no tiene credencial asociada.", HttpStatus.CONFLICT);
        }
        initialCredentialService.requestEmailChange(user, credential, email);
        userRepository.save(user);
        credentialRepository.save(credential);

        instructor.setInstructorType(newType);
        instructorRepository.save(instructor);

        List<UUID> ids = newType == InstructorType.TRANSVERSAL || dto.getProgramIds() == null
                ? List.of()
                : dto.getProgramIds().stream().distinct().toList();
        if (newType == InstructorType.ESPECIFICO && ids.isEmpty()) {
            throw new AcademicException("Un instructor especifico debe indicar el programa al que pertenece.", HttpStatus.BAD_REQUEST);
        }

        if (!sameIds(oldProgramIds, ids)) {
            instructor.setUpdatedAt(java.time.OffsetDateTime.now());
            instructorProgramRepository.deleteAll(currentPrograms);
            instructorProgramRepository.flush();
        }

        for (UUID idProgram : ids) {
            programRepository.findById(idProgram)
                    .orElseThrow(() -> new AcademicException("El programa indicado no existe.", HttpStatus.NOT_FOUND));
            if (sameIds(oldProgramIds, ids)
                    && instructorProgramRepository.existsByInstructor_IdInstructorAndProgram_IdProgram(idInstructor, idProgram)) {
                continue;
            }
            InstructorProgram ip = new InstructorProgram();
            ip.setInstructor(instructor);
            ip.setProgram(programRepository.getReferenceById(idProgram));
            instructorProgramRepository.save(ip);
        }

        if (!oldType.equals(newType)) {
            recordChange(instructor, "instructor", oldType.name(), newType.name(), ChangeAction.UPDATE, "instructor_type", oldType.name() + " -> " + newType.name());
        }

        String newProfile = user.getDocumentNumber() + " / " + user.getFirstName() + " " + user.getLastName()
                + " / " + credential.getEmail();
        if (!oldProfile.equals(newProfile)) {
            recordChange(instructor, "instructor", oldProfile, newProfile, ChangeAction.UPDATE, "instructor_profile", newProfile);
        }

        profileChanges.notifyChanges(user, "instructor_profile_updated", "instructor", Map.of(
                "documento", instructorNotifications.values(oldDocument, user.getDocumentNumber()),
                "nombre", instructorNotifications.values(oldFirstName, user.getFirstName()),
                "apellido", instructorNotifications.values(oldLastName, user.getLastName()),
                "correo", instructorNotifications.values(oldEmail, credential.getEmail()),
                "tipoInstructor", instructorNotifications.values(oldType.name(), newType.name())));

        List<UUID> newProgramIds = ids;

        if (!oldProgramIds.equals(newProgramIds)) {
            List<String> newProgramNames = ids.stream()
                    .map(id -> programRepository.findById(id)
                            .map(program -> program.getProgramName() + " (" + program.getProgramCode() + ")")
                            .orElse(id.toString()))
                    .toList();
            recordChange(instructor, "instructor", oldProgramIds.toString(), newProgramIds.toString(), ChangeAction.UPDATE, "instructor_program", oldProgramIds.toString() + " -> " + newProgramIds.toString());
            instructorNotifications.notifyInstructor(instructor, "instructor_assignment_updated", "Tu asignacion academica cambio",
                    "Coordinacion actualizo tus programas: " + instructorNotifications.listOrNone(oldProgramNames)
                            + " -> " + instructorNotifications.listOrNone(newProgramNames) + ".");
            instructorNotifications.notifyCoordinatorInstructorEvent(
                    "academic_entity_updated",
                    "Asignacion de instructor actualizada",
                    "Se actualizaron los programas del instructor " + instructorNotifications.instructorName(instructor)
                            + ": " + instructorNotifications.listOrNone(oldProgramNames) + " -> " + instructorNotifications.listOrNone(newProgramNames) + ".",
                    instructor,
                    "updated");
        }

        instructorRepository.flush();
        return toResponse(instructor);
    }

    @Override
    @Transactional
    public InstructorResponseDTO reactivate(UUID idInstructor) {
        Instructor instructor = instructorRepository.findById(idInstructor)
                .orElseThrow(() -> new AcademicException("Instructor no encontrado.", HttpStatus.NOT_FOUND));

        User user = instructor.getUser();
        if (user.getAccountStatus() == AccountStatus.ACTIVE) {
            return toResponse(instructor);
        }

        user.setAccountStatus(AccountStatus.ACTIVE);
        if (user.getCredential() != null) {
            user.getCredential().setCredentialStatus(CredentialStatus.ACTIVE);
        }
        userRepository.save(user);
        recordChange(instructor, "instructor", AccountStatus.INACTIVE.name(), AccountStatus.ACTIVE.name(), ChangeAction.REACTIVATE, "account_status", AccountStatus.ACTIVE.name());
        instructorNotifications.notifyCoordinatorInstructorEvent(
                "academic_entity_activated",
                "Instructor activado",
                "Se activo el instructor " + instructorNotifications.instructorName(instructor)
                        + " con documento " + instructorNotifications.emptyAsNone(user.getDocumentNumber()) + ".",
                instructor,
                "activated");

        return toResponse(instructor);
    }
    @Override
    @Transactional
    public InstructorResponseDTO deactivate(UUID idInstructor) {
        Instructor instructor = instructorRepository.findById(idInstructor)
                .orElseThrow(() -> new AcademicException("Instructor no encontrado.", HttpStatus.NOT_FOUND));

        User user = instructor.getUser();
        if (user.getAccountStatus() == AccountStatus.ACTIVE) {
            credentialRepository.lockByUserId(user.getIdUser());
            user.setAccountStatus(AccountStatus.INACTIVE);
            sessions.revokeAll(user.getIdUser());
            if (user.getCredential() != null) {
                user.getCredential().setCredentialStatus(CredentialStatus.INACTIVE);
            }
            userRepository.save(user);
            recordChange(instructor, "instructor", AccountStatus.ACTIVE.name(), AccountStatus.INACTIVE.name(), ChangeAction.DEACTIVATE, "account_status", AccountStatus.INACTIVE.name());
            instructorNotifications.notifyInstructorDeactivated(instructor);
        }
        return toResponse(instructor);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InstructorResponseDTO> findAll() {
        return instructorRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public InstructorResponseDTO findById(UUID idInstructor) {
        Instructor instructor = instructorRepository.findById(idInstructor)
                .orElseThrow(() -> new AcademicException("Instructor no encontrado.", HttpStatus.NOT_FOUND));
        return toResponse(instructor);
    }

    @Override
    @Transactional(readOnly = true)
    public InstructorResponseDTO findByUser(UUID idUser) {
        Instructor instructor = instructorRepository.findAll().stream()
                .filter(i -> i.getUser().getIdUser().equals(idUser))
                .findFirst()
                .orElseThrow(() -> new AcademicException("Instructor no encontrado.", HttpStatus.NOT_FOUND));
        return toResponse(instructor);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InstructorResponseDTO> search(String document, String name, String type) {
        List<Instructor> instructors = instructorRepository.findAll();
        List<InstructorResponseDTO> result = new ArrayList<>();

        for (Instructor instructor : instructors) {
            User user = instructor.getUser();
            String doc = user.getDocumentNumber() == null ? "" : user.getDocumentNumber();
            String fullName = (user.getFirstName() == null ? "" : user.getFirstName()) + " " + (user.getLastName() == null ? "" : user.getLastName());
            boolean matchDocument = document == null || document.isBlank() || doc.toLowerCase().contains(document.toLowerCase());
            boolean matchName = name == null || name.isBlank() || fullName.toLowerCase().contains(name.toLowerCase());
            boolean matchType = type == null || type.isBlank() || instructor.getInstructorType().name().equalsIgnoreCase(type);

            if (matchDocument && matchName && matchType) {
                result.add(toResponse(instructor));
            }
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<InstructorResponseDTO> findEligibleByProgram(UUID idProgram) {
        Program program = programRepository.findById(idProgram)
                .orElseThrow(() -> new AcademicException("El programa indicado no existe.", HttpStatus.NOT_FOUND));

        List<Instructor> instructors = instructorRepository.findAll();
        return instructors.stream()
                .filter(inst -> inst.getUser().getAccountStatus() == AccountStatus.ACTIVE)
                .filter(inst -> inst.getInstructorType() == InstructorType.TRANSVERSAL
                        || instructorProgramRepository.findAll().stream()
                                .anyMatch(ip -> ip.getInstructor().getIdInstructor().equals(inst.getIdInstructor())
                                        && ip.getProgram().getIdProgram().equals(program.getIdProgram())))
                .map(this::toResponse)
                .toList();
    }

    private InstructorResponseDTO createInstructorForUser(User user, InstructorType type, List<UUID> requestedProgramIds, String generatedPassword) {
        AssignRoleRequestDTO roleRequest = new AssignRoleRequestDTO();
        roleRequest.setRole(RoleName.INSTRUCTOR);
        adminRoleService.assignRole(user.getIdUser(), roleRequest);

        if (instructorRepository.existsByUser_IdUser(user.getIdUser())) {
            throw new AcademicException("Este usuario ya está registrado como instructor.", HttpStatus.CONFLICT);
        }
        if (type == InstructorType.ESPECIFICO || (requestedProgramIds != null && !requestedProgramIds.isEmpty())) {
            List<UUID> effectiveProgramIds = requestedProgramIds == null ? List.of() : requestedProgramIds;
            if (type == InstructorType.ESPECIFICO && effectiveProgramIds.isEmpty()) {
                throw new AcademicException("Un instructor específico debe indicar el programa al que pertenece.", HttpStatus.BAD_REQUEST);
            }
            List<UUID> validated = new ArrayList<>();
            for (UUID idProgram : effectiveProgramIds) {
                Program program = programRepository.findById(idProgram)
                        .orElseThrow(() -> new AcademicException("El programa indicado no existe.", HttpStatus.NOT_FOUND));
                if (program != null) {
                    validated.add(idProgram);
                }
            }
            if (validated.stream().distinct().count() != validated.size()) {
                validated = validated.stream().distinct().collect(Collectors.toList());
            }

            Instructor newInstructor = new Instructor();
            newInstructor.setUser(user);
            newInstructor.setInstructorType(type);
            Instructor instructor = instructorRepository.saveAndFlush(newInstructor);

            for (UUID idProgram : validated) {
                Program program = programRepository.getReferenceById(idProgram);
                if (!instructorProgramRepository.existsByInstructor_IdInstructorAndProgram_IdProgram(instructor.getIdInstructor(), idProgram)) {
                    InstructorProgram ip = new InstructorProgram();
                    ip.setInstructor(instructor);
                    ip.setProgram(program);
                    instructorProgramRepository.saveAndFlush(ip);
                }
            }

            recordChange(instructor, "instructor", null, "instructor_type", ChangeAction.CREATE, null, type.name());
            String programIdsString = validated.stream().map(UUID::toString).collect(Collectors.joining(","));
            recordChange(instructor, "instructor", null, "instructor_program", ChangeAction.CREATE, null, programIdsString);
        instructorNotifications.notifyInstructor(instructor, "user_account_created", "Tu usuario de instructor fue creado",
                "Coordinacion creo tu usuario de instructor. Programas asignados: "
                        + instructorNotifications.listOrNone(instructorProgramRepository.findByInstructor_IdInstructor(instructor.getIdInstructor()).stream()
                                .map(ip -> ip.getProgram().getProgramName() + " (" + ip.getProgram().getProgramCode() + ")")
                                .toList())
                        + ".");
            instructorNotifications.notifyCoordinatorInstructorEvent(
                    "academic_entity_created",
                    "Instructor creado",
                    "Se creo el instructor " + instructorNotifications.instructorName(instructor) + " con programas asignados: "
                            + instructorNotifications.listOrNone(instructorProgramRepository.findByInstructor_IdInstructor(instructor.getIdInstructor()).stream()
                                    .map(ip -> ip.getProgram().getProgramName() + " (" + ip.getProgram().getProgramCode() + ")")
                                    .toList())
                            + ".",
                    instructor,
                    "created");
            return toResponse(instructor, generatedPassword);
        }

        Instructor instructor = new Instructor();
        instructor.setUser(user);
        instructor.setInstructorType(type);
        instructor = instructorRepository.saveAndFlush(instructor);
        recordChange(instructor, "instructor", null, "instructor_type", ChangeAction.CREATE, null, type.name());
        instructorNotifications.notifyInstructor(instructor, "user_account_created", "Tu usuario de instructor fue creado",
                "Coordinacion creo tu usuario de instructor.");
        instructorNotifications.notifyCoordinatorInstructorEvent(
                "academic_entity_created",
                "Instructor creado",
                "Se creo el instructor " + instructorNotifications.instructorName(instructor) + ".",
                instructor,
                "created");
        return toResponse(instructor, generatedPassword);
    }

    private InstructorResponseDTO toResponse(Instructor instructor) {
        return toResponse(instructor, null);
    }

    private InstructorResponseDTO toResponse(Instructor instructor, String initialPassword) {
        UUID idInstructor = instructor.getIdInstructor();
        List<InstructorProgram> programs = instructorProgramRepository.findAll().stream()
                .filter(ip -> ip.getInstructor().getIdInstructor().equals(idInstructor))
                .toList();
        List<UUID> chipIds = recordEnvironmentRepository.findDistinctChipIdsByInstructor(idInstructor);
        return new InstructorResponseDTO(instructor, programs, initialPassword, chipIds);
    }

    private boolean sameIds(List<UUID> currentIds, List<UUID> newIds) {
        return currentIds.size() == newIds.size() && currentIds.containsAll(newIds) && newIds.containsAll(currentIds);
    }

    private String normalizeDocument(String document) {
        return document == null ? "" : document.trim().replace(" ", "").replace("-", "");
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private void recordChange(Instructor instructor, String entityName, String oldValue, String newValue, ChangeAction action, String fieldName, String fieldValue) {
        ChangeHistory history = new ChangeHistory();
        history.setEntityName(entityName);
        history.setEntityId(instructor.getIdInstructor());
        history.setFieldName(fieldName != null ? fieldName : "instructor");
        history.setOldValue(oldValue);
        history.setNewValue(newValue != null ? newValue : fieldValue);
        history.setAction(action);
        changeHistoryRepository.save(history);
    }

}
