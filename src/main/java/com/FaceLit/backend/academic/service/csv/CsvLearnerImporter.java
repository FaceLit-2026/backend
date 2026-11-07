package com.FaceLit.backend.academic.service.csv;

import com.FaceLit.backend.academic.dto.request.academic.UserChipRequestDTO;
import com.FaceLit.backend.academic.dto.response.academic.CsvUploadResponseDTO;
import com.FaceLit.backend.academic.model.academic.Apprentice;
import com.FaceLit.backend.academic.model.academic.Chip;
import com.FaceLit.backend.academic.model.academic.CsvPendingTransfer;
import com.FaceLit.backend.academic.model.academic.UserChip;
import com.FaceLit.backend.academic.model.enums.AcademicState;
import com.FaceLit.backend.academic.model.enums.PendingTransferStatus;
import com.FaceLit.backend.academic.repository.ApprenticeRepository;
import com.FaceLit.backend.academic.repository.ChipRepository;
import com.FaceLit.backend.academic.repository.CsvPendingTransferRepository;
import com.FaceLit.backend.academic.repository.UserChipRepository;
import com.FaceLit.backend.academic.service.academic.UserChipService;
import com.FaceLit.backend.auth.model.enums.RoleName;
import com.FaceLit.backend.auth.model.security.User;
import com.FaceLit.backend.auth.repository.roleandpermission.UserRoleRepository;
import com.FaceLit.backend.auth.repository.security.UserRepository;
import java.time.OffsetDateTime;
import java.util.Map;

/** Learner CSV membership and pending transfer creation. */
@org.springframework.stereotype.Component
public class CsvLearnerImporter {
    private final ChipRepository chipRepository;
    private final UserRepository userRepository;
    private final ApprenticeRepository apprenticeRepository;
    private final UserChipRepository userChipRepository;
    private final CsvPendingTransferRepository pendingTransferRepository;
    private final UserChipService userChipService;
    private final UserRoleRepository userRoleRepository;
    private final CsvUserImporter userImporter;

    public CsvLearnerImporter(
            ChipRepository chipRepository,
            UserRepository userRepository,
            ApprenticeRepository apprenticeRepository,
            UserChipRepository userChipRepository,
            CsvPendingTransferRepository pendingTransferRepository,
            UserChipService userChipService,
            UserRoleRepository userRoleRepository,
            CsvUserImporter userImporter) {
        this.chipRepository = chipRepository;
        this.userRepository = userRepository;
        this.apprenticeRepository = apprenticeRepository;
        this.userChipRepository = userChipRepository;
        this.pendingTransferRepository = pendingTransferRepository;
        this.userChipService = userChipService;
        this.userRoleRepository = userRoleRepository;
        this.userImporter = userImporter;
    }

    public void processApprentice(CsvRow row, Map<String, Chip> chips, CsvUploadResponseDTO result) {
        String document = row.value("documento");
        Chip chip = chips.get(row.value("ficha_codigo"));
        if (chip == null) {
            chip = chipRepository.findByChipCode(row.value("ficha_codigo")).orElse(null);
        }
        if (!CsvValidationRules.DOCUMENT.matcher(document).matches()) {
                result.getErroresDeReferencia().add(row.error("El documento de la fila " + row.number() + " debe tener entre 6 y 15 dígitos numéricos."));
            return;
        }
        if (chip == null) {
            result.getErroresDeReferencia().add(row.error("La ficha '" + row.value("ficha_codigo") + "' indicada en la fila " + row.number() + " no existe."));
            return;
        }
        User user = userRepository.findByDocumentNumber(document).orElse(null);
        if (user == null) {
            user = userImporter.findOrCreateUser(row, result);
            if (user == null) {
                return;
            }
            userImporter.assignRole(user, RoleName.APPRENTICE);
            createApprentice(user);
            assignInitialChip(user, chip);
            result.getCreados()
                    .add(new CsvUploadResponseDTO.CsvRowResult(row.number(), "aprendiz", document + " creado"));
            return;
        }
        if (!canUseExistingUserAsApprentice(user)) {
            result.getErroresDeReferencia().add(row.error("El documento " + document + " ya pertenece a un usuario con otro rol. No se puede registrar como aprendiz."));
            return;
        }
        // Existing apprentices must also apply personal changes and retry pending access instructions.
        user = userImporter.findOrCreateUser(row, result);
        if (user == null) {
            return;
        }
        userImporter.assignRole(user, RoleName.APPRENTICE);
        User resolvedUser = user;
        Apprentice current = apprenticeRepository.findByUser_IdUser(resolvedUser.getIdUser())
                .orElse(null);
        UserChip currentAssignment = userChipRepository.findByApprentice_User_IdUserAndState(resolvedUser.getIdUser(), AcademicState.ACTIVE)
                .orElse(null);
        if (current == null) {
            createApprentice(user);
            if (currentAssignment == null) {
                assignInitialChip(user, chip);
            }
            result.getCreados()
                    .add(new CsvUploadResponseDTO.CsvRowResult(row.number(), "aprendiz", document + " creado"));
            return;
        }
        if (currentAssignment == null) {
            assignInitialChip(user, chip);
            result.getActualizados()
                    .add(new CsvUploadResponseDTO.CsvRowResult(row.number(), "aprendiz", document + " actualizado"));
            return;
        }
        if (currentAssignment.getChip().getIdChip().equals(chip.getIdChip())) {
            result.getActualizados()
                    .add(new CsvUploadResponseDTO.CsvRowResult(row.number(), "aprendiz", document + " actualizado"));
            return;
        }
        pendingTransferRepository.findByUser_IdUserAndStatus(user.getIdUser(), PendingTransferStatus.PENDING)
                .ifPresent(previous -> {
                    previous.setStatus(PendingTransferStatus.CANCELLED);
                    previous.setResolvedAt(OffsetDateTime.now());
                    pendingTransferRepository.save(previous);
                });
        CsvPendingTransfer pending = new CsvPendingTransfer();
        pending.setUser(user);
        pending.setCurrentChip(currentAssignment.getChip());
        pending.setProposedChip(chip);
        pending.setSourceRowNumber(row.number());
        pending.setStatus(PendingTransferStatus.PENDING);
        pending = pendingTransferRepository.save(pending);
        result.getTrasladosPendientes()
                .add(new CsvUploadResponseDTO.CsvPendingTransferResult(pending.getIdPendingTransfer(), row.number(),
                        user.getFirstName() + " " + user.getLastName(), currentAssignment.getChip().getChipCode(),
                        chip.getChipCode()));
    }

    private Apprentice createApprentice(User user) {
        Apprentice apprentice = new Apprentice();
        apprentice.setUser(user);
        return apprenticeRepository.save(apprentice);
    }

    private void assignInitialChip(User user, Chip chip) {
        UserChipRequestDTO assignment = new UserChipRequestDTO();
        assignment.setIdUser(user.getIdUser());
        userChipService.assignInitialChip(chip.getIdChip(), assignment);
    }

    private boolean canUseExistingUserAsApprentice(User user) {
        return userRoleRepository.findByUserId(user.getIdUser())
                .map(userRole -> userRole.getRole().getNameRole() == RoleName.APPRENTICE)
                .orElse(true);
    }

}
