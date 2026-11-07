package com.FaceLit.backend.academic.service.csv;

import com.FaceLit.backend.academic.dto.request.academic.TransferChipRequestDTO;
import com.FaceLit.backend.academic.dto.response.academic.PendingTransferResponseDTO;
import com.FaceLit.backend.academic.dto.response.academic.UserChipResponseDTO;
import com.FaceLit.backend.academic.exception.AcademicException;
import com.FaceLit.backend.academic.model.academic.ChangeHistory;
import com.FaceLit.backend.academic.model.academic.CsvPendingTransfer;
import com.FaceLit.backend.academic.model.academic.UserChip;
import com.FaceLit.backend.academic.model.enums.AcademicState;
import com.FaceLit.backend.academic.model.enums.ChangeAction;
import com.FaceLit.backend.academic.model.enums.PendingTransferStatus;
import com.FaceLit.backend.academic.repository.ApprenticeRepository;
import com.FaceLit.backend.academic.repository.ChangeHistoryRepository;
import com.FaceLit.backend.academic.repository.CsvPendingTransferRepository;
import com.FaceLit.backend.academic.repository.UserChipRepository;
import com.FaceLit.backend.academic.service.academic.UserChipService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/** Pending transfer decisions and their audit trail; called inside the facade transaction. */
@org.springframework.stereotype.Component
public class CsvTransferService {
    private final ApprenticeRepository apprenticeRepository;
    private final UserChipRepository userChipRepository;
    private final CsvPendingTransferRepository pendingTransferRepository;
    private final ChangeHistoryRepository changeHistoryRepository;
    private final UserChipService userChipService;
    private final CsvImportNotifier notifier;

    public CsvTransferService(
            ApprenticeRepository apprenticeRepository,
            UserChipRepository userChipRepository,
            CsvPendingTransferRepository pendingTransferRepository,
            ChangeHistoryRepository changeHistoryRepository,
            UserChipService userChipService,
            CsvImportNotifier notifier) {
        this.apprenticeRepository = apprenticeRepository;
        this.userChipRepository = userChipRepository;
        this.pendingTransferRepository = pendingTransferRepository;
        this.changeHistoryRepository = changeHistoryRepository;
        this.userChipService = userChipService;
        this.notifier = notifier;
    }

    public List<PendingTransferResponseDTO> pendingTransfers() {
        return pendingTransferRepository.findByStatusOrderByCreatedAtDesc(PendingTransferStatus.PENDING).stream()
                .map(PendingTransferResponseDTO::new).toList();
    }

    public UserChipResponseDTO acceptPendingTransfer(UUID idPendingTransfer) {
        CsvPendingTransfer pending = getPending(idPendingTransfer);
        apprenticeRepository.findByUser_IdUser(pending.getUser().getIdUser())
                .orElseThrow(() -> new AcademicException("El aprendiz no existe.", HttpStatus.BAD_REQUEST));
        TransferChipRequestDTO dto = new TransferChipRequestDTO();
        dto.setIdNewChip(pending.getProposedChip().getIdChip());
        UserChipResponseDTO response = userChipService.transferChip(pending.getUser().getIdUser(), dto);
        pending.setStatus(PendingTransferStatus.ACCEPTED);
        pending.setResolvedAt(OffsetDateTime.now());
        pendingTransferRepository.save(pending);
        recordCsvHistory(pending, ChangeAction.CSV_CONFIRM);
        notifier.notifyTransferApplied(pending);
        return response;
    }

    public void cancelPendingTransfer(UUID idPendingTransfer) {
        CsvPendingTransfer pending = getPending(idPendingTransfer);
        pending.setStatus(PendingTransferStatus.CANCELLED);
        pending.setResolvedAt(OffsetDateTime.now());
        pendingTransferRepository.save(pending);
        recordCsvHistory(pending, ChangeAction.CSV_CANCEL);
        notifier.notifyTransferRejected(pending);
    }

    private CsvPendingTransfer getPending(UUID id) {
        CsvPendingTransfer pending = pendingTransferRepository.findById(id)
                .orElseThrow(() -> new AcademicException("Traslado pendiente no encontrado.", HttpStatus.NOT_FOUND));
        if (pending.getStatus() != PendingTransferStatus.PENDING) {
            throw new AcademicException("Este traslado pendiente ya fue resuelto anteriormente.", HttpStatus.CONFLICT);
        }
        return pending;
    }

    private void recordCsvHistory(CsvPendingTransfer pending, ChangeAction action) {
        ChangeHistory history = new ChangeHistory();
        history.setEntityName("apprentice_chip");
        history.setEntityId(userChipRepository.findByApprentice_User_IdUserAndState(
                pending.getUser().getIdUser(), AcademicState.ACTIVE)
                .map(UserChip::getIdUserChip)
                .orElse(pending.getUser().getIdUser()));
        history.setFieldName("chip");
        history.setOldValue(pending.getCurrentChip().getChipCode());
        history.setNewValue(pending.getProposedChip().getChipCode());
        history.setAction(action);
        changeHistoryRepository.save(history);
    }

}
