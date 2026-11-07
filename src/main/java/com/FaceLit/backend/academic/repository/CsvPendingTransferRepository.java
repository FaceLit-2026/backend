package com.FaceLit.backend.academic.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.FaceLit.backend.academic.model.academic.CsvPendingTransfer;
import com.FaceLit.backend.academic.model.enums.PendingTransferStatus;

public interface CsvPendingTransferRepository extends JpaRepository<CsvPendingTransfer, UUID> {

    List<CsvPendingTransfer> findByStatusOrderByCreatedAtDesc(PendingTransferStatus status);

    Optional<CsvPendingTransfer> findByUser_IdUserAndStatus(UUID idUser, PendingTransferStatus status);
}
