package com.FaceLit.backend.academic.service.serviceImpl.academic;

import com.FaceLit.backend.academic.dto.request.academic.TransferChipRequestDTO;
import com.FaceLit.backend.academic.dto.request.academic.UserChipRequestDTO;
import com.FaceLit.backend.academic.dto.response.academic.UserChipResponseDTO;
import com.FaceLit.backend.academic.exception.AcademicException;
import com.FaceLit.backend.academic.model.academic.Apprentice;
import com.FaceLit.backend.academic.model.academic.ChangeHistory;
import com.FaceLit.backend.academic.model.academic.Chip;
import com.FaceLit.backend.academic.model.academic.UserChip;
import com.FaceLit.backend.academic.model.enums.AcademicState;
import com.FaceLit.backend.academic.model.enums.ChangeAction;
import com.FaceLit.backend.academic.repository.ApprenticeRepository;
import com.FaceLit.backend.academic.repository.ChangeHistoryRepository;
import com.FaceLit.backend.academic.repository.ChipRepository;
import com.FaceLit.backend.academic.repository.UserChipRepository;
import com.FaceLit.backend.academic.service.academic.UserChipService;
import com.FaceLit.backend.academic.service.notification.LearnerNotificationService;
import com.FaceLit.backend.auth.dto.request.roleandpermission.AssignRoleRequestDTO;
import com.FaceLit.backend.auth.model.enums.AccountStatus;
import com.FaceLit.backend.auth.model.enums.CredentialStatus;
import com.FaceLit.backend.auth.model.enums.RoleName;
import com.FaceLit.backend.auth.model.security.Credential;
import com.FaceLit.backend.auth.model.security.User;
import com.FaceLit.backend.auth.repository.roleandpermission.UserRoleRepository;
import com.FaceLit.backend.auth.repository.security.CredentialRepository;
import com.FaceLit.backend.auth.repository.security.UserRepository;
import com.FaceLit.backend.auth.service.roleandpermission.AdminRoleService;
import com.FaceLit.backend.auth.service.security.InitialCredentialService;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserChipServiceImpl implements UserChipService {
    private final UserChipRepository userChipRepository;
    private final ApprenticeRepository apprenticeRepository;
    private final UserRepository userRepository;
    private final CredentialRepository credentialRepository;
    private final ChipRepository chipRepository;
    private final ChangeHistoryRepository changeHistoryRepository;
    private final InitialCredentialService initialCredentialService;
    private final AdminRoleService adminRoleService;
    private final UserRoleRepository userRoleRepository;
    private final LearnerNotificationService learnerNotifications;

    public UserChipServiceImpl(
            UserChipRepository userChipRepository,
            ApprenticeRepository apprenticeRepository,
            UserRepository userRepository,
            CredentialRepository credentialRepository,
            ChipRepository chipRepository,
            ChangeHistoryRepository changeHistoryRepository,
            InitialCredentialService initialCredentialService,
            AdminRoleService adminRoleService,
            UserRoleRepository userRoleRepository,
            LearnerNotificationService learnerNotifications) {
        this.userChipRepository = userChipRepository;
        this.apprenticeRepository = apprenticeRepository;
        this.userRepository = userRepository;
        this.credentialRepository = credentialRepository;
        this.chipRepository = chipRepository;
        this.changeHistoryRepository = changeHistoryRepository;
        this.initialCredentialService = initialCredentialService;
        this.adminRoleService = adminRoleService;
        this.userRoleRepository = userRoleRepository;
        this.learnerNotifications = learnerNotifications;
    }

    @Override
    @Transactional
    public UserChipResponseDTO assignInitialChip(UUID idChip, UserChipRequestDTO dto) {
        Chip chip = chipRepository.findById(idChip)
                .orElseThrow(() -> new AcademicException("Ficha no encontrada.", HttpStatus.NOT_FOUND));

        if (chip.getState() != AcademicState.ACTIVE) {
            throw new AcademicException("La ficha no está activa.", HttpStatus.BAD_REQUEST);
        }

        final String[] generatedPasswordHolder = {null};
        final Credential[] generatedCredentialHolder = {null};
        User user;
        if (dto.getIdUser() != null) {
            user = userRepository.findById(dto.getIdUser())
                    .orElseThrow(() -> new AcademicException("Usuario no encontrado.", HttpStatus.NOT_FOUND));
        } else {
            String document = dto.getDocumento() == null ? "" : dto.getDocumento().trim();
            if (!document.matches("\\d{6,15}")) {
                throw new AcademicException("El documento debe contener solo dígitos y tener entre 6 y 15 caracteres.", HttpStatus.BAD_REQUEST);
            }
            user = userRepository.findByDocumentNumber(document).orElseGet(() -> {
                String email = dto.getCorreo() == null ? "" : dto.getCorreo().trim().toLowerCase(Locale.ROOT);
                if (credentialRepository.existsByEmailIgnoreCase(email)) {
                    throw new AcademicException("El correo ya esta registrado en otro usuario.", HttpStatus.CONFLICT);
                }

                User newUser = new User();
                newUser.setDocumentNumber(document);
                newUser.setFirstName(dto.getNombre());
                newUser.setLastName(dto.getApellido());
                newUser.setAccountStatus(AccountStatus.ACTIVE);
                newUser = userRepository.saveAndFlush(newUser);

                String generatedPassword = initialCredentialService.generateInitialPassword();
                generatedPasswordHolder[0] = generatedPassword;
                Credential credential = new Credential();
                credential.setUser(newUser);
                credential.setEmail(email);
                initialCredentialService.encodeGeneratedPassword(credential, generatedPassword);
                credential.setCredentialStatus(CredentialStatus.ACTIVE);
                credential.setFailedAttempts(0);
                generatedCredentialHolder[0] = credentialRepository.saveAndFlush(credential);
                return newUser;
            });
        }

        ensureExistingUserCanBeApprentice(user);

        Apprentice apprentice = findOrCreateApprentice(user);

        if (userChipRepository.existsByApprentice_User_IdUserAndState(user.getIdUser(), AcademicState.ACTIVE)) {
            throw new AcademicException("Este aprendiz ya tiene una ficha activa. Usa el traslado para cambiarlo de ficha.", HttpStatus.CONFLICT);
        }

        AssignRoleRequestDTO roleRequest = new AssignRoleRequestDTO();
        roleRequest.setRole(RoleName.APPRENTICE);
        adminRoleService.assignRole(user.getIdUser(), roleRequest);

        UserChip userChip = new UserChip();
        userChip.setApprentice(apprentice);
        userChip.setChip(chip);
        userChip.setState(AcademicState.ACTIVE);
        userChip.setAssignmentDate(OffsetDateTime.now());
        userChip = userChipRepository.saveAndFlush(userChip);

        recordChange(userChip, "apprentice_chip", null, chip.getChipCode(), ChangeAction.CREATE, "chip");
        learnerNotifications.notifyApprenticeCreated(user, chip, userChip);
        if (generatedPasswordHolder[0] != null && generatedCredentialHolder[0] != null) {
            initialCredentialService.sendWelcomeAfterCommit(
                    user,
                    generatedCredentialHolder[0],
                    RoleName.APPRENTICE,
                    generatedPasswordHolder[0],
                    chip.getChipCode(),
                    chip.getProgram().getProgramName());
        }
        return new UserChipResponseDTO(userChip);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserChipResponseDTO> findApprenticesByChip(UUID idChip) {
        Chip chip = chipRepository.findById(idChip)
                .orElseThrow(() -> new AcademicException("Ficha no encontrada.", HttpStatus.NOT_FOUND));
        return userChipRepository.findByChip_IdChip(chip.getIdChip()).stream()
                .filter(uc -> uc.getState() == AcademicState.ACTIVE)
                .map(UserChipResponseDTO::new)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserChipResponseDTO> findApprenticesByChips(List<UUID> idChips) {
        if (idChips == null || idChips.isEmpty()) {
            return List.of();
        }

        List<UUID> uniqueIds = idChips.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();

        if (uniqueIds.isEmpty()) {
            return List.of();
        }

        return userChipRepository.findByChip_IdChipInAndState(uniqueIds, AcademicState.ACTIVE).stream()
                .map(UserChipResponseDTO::new)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public UserChipResponseDTO getActiveChipByUser(UUID idUser) {
        User user = userRepository.findById(idUser)
                .orElseThrow(() -> new AcademicException("Usuario no encontrado.", HttpStatus.NOT_FOUND));

        UserChip chip = userChipRepository.findByApprentice_User_IdUserAndState(user.getIdUser(), AcademicState.ACTIVE)
                .orElseThrow(() -> new AcademicException("El aprendiz no tiene una ficha activa para trasladar.", HttpStatus.BAD_REQUEST));

        return new UserChipResponseDTO(chip);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserChipResponseDTO> getChipHistoryByUser(UUID idUser) {
        User user = userRepository.findById(idUser)
                .orElseThrow(() -> new AcademicException("Usuario no encontrado.", HttpStatus.NOT_FOUND));

        return userChipRepository.findByApprentice_User_IdUser(user.getIdUser()).stream()
                .sorted(Comparator.comparing(UserChip::getAssignmentDate).reversed())
                .map(UserChipResponseDTO::new)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserChipResponseDTO> getTransferTargets(UUID idUser) {
        User user = userRepository.findById(idUser)
                .orElseThrow(() -> new AcademicException("Usuario no encontrado.", HttpStatus.NOT_FOUND));

        Optional<UserChip> current = userChipRepository.findByApprentice_User_IdUserAndState(user.getIdUser(), AcademicState.ACTIVE);

        if (current.isEmpty()) {
            throw new AcademicException("El aprendiz no tiene una ficha activa para trasladar.", HttpStatus.BAD_REQUEST);
        }

        UUID currentChipId = current.get().getChip().getIdChip();
        UUID currentProgramId = current.get().getChip().getProgram().getIdProgram();
        return chipRepository.findByState(AcademicState.ACTIVE).stream()
                .filter(chip -> !chip.getIdChip().equals(currentChipId))
                .filter(chip -> chip.getProgram().getIdProgram().equals(currentProgramId))
                .map(chip -> new UserChipResponseDTO(buildTransferTarget(user, chip)))
                .toList();
    }

    @Override
    @Transactional
    public UserChipResponseDTO updateApprentice(UUID idUser, UserChipRequestDTO dto) {
        User user = userRepository.findById(idUser)
                .orElseThrow(() -> new AcademicException("Usuario no encontrado.", HttpStatus.NOT_FOUND));
        Credential credential = credentialRepository.findByUser(user)
                .orElseThrow(() -> new AcademicException("El aprendiz no tiene credencial asociada.", HttpStatus.CONFLICT));
        UserChip activeChip = userChipRepository.findByApprentice_User_IdUserAndState(user.getIdUser(), AcademicState.ACTIVE)
                .orElseThrow(() -> new AcademicException("El aprendiz no tiene una ficha activa.", HttpStatus.BAD_REQUEST));

        String document = dto.getDocumento() == null ? "" : dto.getDocumento().trim();
        String email = dto.getCorreo() == null ? "" : dto.getCorreo().trim().toLowerCase(Locale.ROOT);
        if (!document.matches("\\d{6,15}")) {
            throw new AcademicException("El documento debe contener solo digitos y tener entre 6 y 15 caracteres.", HttpStatus.BAD_REQUEST);
        }
        if (email.isBlank()) {
            throw new AcademicException("El correo es obligatorio.", HttpStatus.BAD_REQUEST);
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
        String oldEmail = credential.getEmail();

        user.setDocumentNumber(document);
        user.setFirstName(dto.getNombre().trim());
        user.setLastName(dto.getApellido().trim());
        userRepository.save(user);

        initialCredentialService.requestEmailChange(user, credential, email);
        credentialRepository.saveAndFlush(credential);

        learnerNotifications.notifyApprenticeProfileUpdated(user, activeChip, oldDocument, oldFirstName, oldLastName, oldEmail, credential.getEmail());
        return new UserChipResponseDTO(activeChip);
    }

    @Override
    @Transactional
    public UserChipResponseDTO transferChip(UUID idUser, TransferChipRequestDTO dto) {
        if (dto == null || dto.getIdNewChip() == null) {
            throw new AcademicException("Debes seleccionar una ficha destino.", HttpStatus.BAD_REQUEST);
        }

        User user = userRepository.findById(idUser)
                .orElseThrow(() -> new AcademicException("Usuario no encontrado.", HttpStatus.NOT_FOUND));

        UserChip current = userChipRepository.findByApprentice_User_IdUserAndState(user.getIdUser(), AcademicState.ACTIVE)
                .orElseThrow(() -> new AcademicException("El aprendiz no tiene una ficha activa para trasladar.", HttpStatus.BAD_REQUEST));

        Chip destination = chipRepository.findById(dto.getIdNewChip())
                .orElseThrow(() -> new AcademicException("La ficha destino no existe.", HttpStatus.NOT_FOUND));

        if (destination.getState() != AcademicState.ACTIVE) {
            throw new AcademicException("La ficha destino no está activa.", HttpStatus.BAD_REQUEST);
        }

        if (destination.getIdChip().equals(current.getChip().getIdChip())) {
            throw new AcademicException("La ficha destino debe ser diferente a la actual.", HttpStatus.BAD_REQUEST);
        }

        if (!destination.getProgram().getIdProgram().equals(current.getChip().getProgram().getIdProgram())) {
            throw new AcademicException(destination.getChipCode(), HttpStatus.BAD_REQUEST);
        }

        String previousCode = current.getChip().getChipCode();
        current.setState(AcademicState.INACTIVE);
        // La base de datos solo permite una ficha ACTIVE por aprendiz.
        // Forzamos primero el UPDATE de la asignacion anterior para evitar
        // que Hibernate intente insertar la nueva relacion antes de desactivarla.
        userChipRepository.saveAndFlush(current);

        UserChip newAssignment = new UserChip();
        newAssignment.setApprentice(current.getApprentice());
        newAssignment.setChip(destination);
        newAssignment.setState(AcademicState.ACTIVE);
        newAssignment.setAssignmentDate(OffsetDateTime.now());
        newAssignment = userChipRepository.saveAndFlush(newAssignment);

        recordChange(newAssignment, "apprentice_chip", previousCode, destination.getChipCode(), ChangeAction.UPDATE, "chip");
        learnerNotifications.notifyTransfer(user, current.getChip(), destination, newAssignment);
        return new UserChipResponseDTO(newAssignment);
    }

    private void ensureExistingUserCanBeApprentice(User user) {
        userRoleRepository.findByUserId(user.getIdUser()).ifPresent(userRole -> {
            RoleName currentRole = userRole.getRole().getNameRole();
            if (currentRole != RoleName.APPRENTICE) {
                throw new AcademicException(
                        "El documento ya pertenece a un usuario con rol " + currentRole.name() + ". No se puede registrar como aprendiz.",
                        HttpStatus.CONFLICT);
            }
        });
    }

    private Apprentice findOrCreateApprentice(User user) {
        return apprenticeRepository.findByUser_IdUser(user.getIdUser())
                .orElseGet(() -> {
                    Apprentice apprentice = new Apprentice();
                    apprentice.setUser(user);
                    return apprenticeRepository.saveAndFlush(apprentice);
                });
    }

    private UserChip buildTransferTarget(User user, Chip chip) {
        UserChip temp = new UserChip();
        temp.setIdUserChip(UUID.randomUUID());
        Apprentice apprentice = apprenticeRepository.findByUser_IdUser(user.getIdUser()).orElseGet(() -> {
            Apprentice transientApprentice = new Apprentice();
            transientApprentice.setUser(user);
            return transientApprentice;
        });
        temp.setApprentice(apprentice);
        temp.setChip(chip);
        temp.setState(AcademicState.ACTIVE);
        temp.setAssignmentDate(OffsetDateTime.now());
        return temp;
    }

    private void recordChange(UserChip userChip, String entityName, String oldValue, String newValue, ChangeAction action, String fieldName) {
        ChangeHistory history = new ChangeHistory();
        history.setEntityName(entityName);
        history.setEntityId(userChip.getIdUserChip());
        history.setFieldName(fieldName);
        history.setOldValue(oldValue);
        history.setNewValue(newValue);
        history.setAction(action);
        changeHistoryRepository.save(history);
    }

}
