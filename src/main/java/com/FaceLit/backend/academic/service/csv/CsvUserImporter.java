package com.FaceLit.backend.academic.service.csv;

import com.FaceLit.backend.academic.dto.response.academic.CsvUploadResponseDTO;
import com.FaceLit.backend.academic.exception.AcademicException;
import com.FaceLit.backend.academic.model.academic.Chip;
import com.FaceLit.backend.academic.model.academic.Program;
import com.FaceLit.backend.academic.repository.ChipRepository;
import com.FaceLit.backend.academic.repository.ProgramRepository;
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
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/** User identity and credential coordination for CSV imports. */
@org.springframework.stereotype.Component
public class CsvUserImporter {
    private final ProgramRepository programRepository;
    private final ChipRepository chipRepository;
    private final UserRepository userRepository;
    private final CredentialRepository credentialRepository;
    private final InitialCredentialService initialCredentialService;
    private final AdminRoleService adminRoleService;
    private final CsvImportNotifier notifier;

    public CsvUserImporter(
            ProgramRepository programRepository,
            ChipRepository chipRepository,
            UserRepository userRepository,
            CredentialRepository credentialRepository,
            InitialCredentialService initialCredentialService,
            AdminRoleService adminRoleService,
            CsvImportNotifier notifier) {
        this.programRepository = programRepository;
        this.chipRepository = chipRepository;
        this.userRepository = userRepository;
        this.credentialRepository = credentialRepository;
        this.initialCredentialService = initialCredentialService;
        this.adminRoleService = adminRoleService;
        this.notifier = notifier;
    }

    public User findOrCreateUser(CsvRow row, CsvUploadResponseDTO result) {
        User existing = userRepository.findByDocumentNumber(row.value("documento")).orElse(null);
        if (existing != null) {
            String oldFirstName = existing.getFirstName();
            String oldLastName = existing.getLastName();
            String newEmail = row.value("correo").toLowerCase(Locale.ROOT);
            Credential credential = credentialRepository.findByUser(existing).orElse(null);
            String oldEmail = credential != null ? credential.getEmail() : "";

            existing.setFirstName(row.value("nombre"));
            existing.setLastName(row.value("apellido"));
            existing = userRepository.save(existing);

            if (credential == null) {
                if (credentialRepository.existsByEmailIgnoreCase(newEmail)) {
                    result.getErroresDeReferencia().add(row.error("El correo " + newEmail + " ya esta registrado en otro usuario. Corrige el correo o usa el documento correcto."));
                    return null;
                }
                String password = initialCredentialService.generateInitialPassword();
                credential = new Credential();
                credential.setUser(existing);
                credential.setEmail(newEmail);
                initialCredentialService.encodeGeneratedPassword(credential, password);
                credential.setCredentialStatus(CredentialStatus.ACTIVE);
                credential.setFailedAttempts(0);
                credentialRepository.save(credential);
                result.getContrasenasGeneradas().add(new CsvUploadResponseDTO.CsvGeneratedPasswordResult(
                        row.number(),
                        row.value("tipo"),
                        existing.getDocumentNumber(),
                        credential.getEmail(),
                        password));
                initialCredentialService.sendWelcomeAfterCommit(existing, credential, roleForRow(row), password,
                        confirmedChipCode(row), confirmedProgramName(row));
            } else if (!oldEmail.equalsIgnoreCase(newEmail)) {
                UUID existingUserId = existing.getIdUser();
                credentialRepository.findByEmailIgnoreCase(newEmail)
                        .filter(item -> !item.getUser().getIdUser().equals(existingUserId))
                        .ifPresent(item -> {
                            throw new AcademicException("El correo " + newEmail
                                    + " ya esta registrado en otro usuario. Corrige el correo o usa el documento correcto.",
                                    HttpStatus.CONFLICT);
                        });
                initialCredentialService.requestEmailChange(existing, credential, newEmail);
            } else {
                initialCredentialService.retryWelcome(existing);
            }
            notifier.notifyCsvPersonalUpdate(row, existing, oldFirstName, oldLastName, oldEmail, credential.getEmail());
            return existing;
        }
        String email = row.value("correo").toLowerCase(Locale.ROOT);
        if (credentialRepository.existsByEmailIgnoreCase(email)) {
            result.getErroresDeReferencia().add(row.error("El correo " + email + " ya esta registrado en otro usuario. Corrige el correo o usa el documento correcto."));
            return null;
        }

        User user = new User();
        user.setDocumentNumber(row.value("documento"));
        user.setFirstName(row.value("nombre"));
        user.setLastName(row.value("apellido"));
        user.setAccountStatus(AccountStatus.ACTIVE);
        user = userRepository.save(user);
        String password = initialCredentialService.generateInitialPassword();
        Credential credential = new Credential();
        credential.setUser(user);
        credential.setEmail(email);
        initialCredentialService.encodeGeneratedPassword(credential, password);
        credential.setCredentialStatus(CredentialStatus.ACTIVE);
        credential.setFailedAttempts(0);
        credentialRepository.save(credential);
        result.getContrasenasGeneradas().add(new CsvUploadResponseDTO.CsvGeneratedPasswordResult(
                row.number(),
                row.value("tipo"),
                user.getDocumentNumber(),
                credential.getEmail(),
                password));
        initialCredentialService.sendWelcomeAfterCommit(user, credential, roleForRow(row), password,
                confirmedChipCode(row), confirmedProgramName(row));
        return user;
    }

    public void assignRole(User user, RoleName role) {
        AssignRoleRequestDTO request = new AssignRoleRequestDTO();
        request.setRole(role);
        adminRoleService.assignRole(user.getIdUser(), request);
    }

    private RoleName roleForRow(CsvRow row) {
        return row.value("tipo").equals("instructor") ? RoleName.INSTRUCTOR : RoleName.APPRENTICE;
    }

    private String confirmedChipCode(CsvRow row) {
        return row.value("tipo").equals("aprendiz") ? valueOrNull(row.value("ficha_codigo")) : null;
    }

    private String confirmedProgramName(CsvRow row) {
        String programCode = row.value("programa_codigo");
        if (!programCode.isBlank()) {
            return programRepository.findByProgramCodeIgnoreCase(programCode)
                    .map(Program::getProgramName)
                    .orElse(null);
        }
        String chipCode = row.value("ficha_codigo");
        if (!chipCode.isBlank()) {
            return chipRepository.findByChipCode(chipCode)
                    .map(Chip::getProgram)
                    .map(Program::getProgramName)
                    .orElse(null);
        }
        return null;
    }

    private String valueOrNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

}
