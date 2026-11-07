package com.FaceLit.backend.academic.dto.response.academic;

import java.util.UUID;

import com.FaceLit.backend.academic.model.academic.CsvPendingTransfer;

import lombok.Getter;

@Getter
public class PendingTransferResponseDTO {

    private final UUID idPendingTransfer;
    private final UUID idUser;
    private final int fila;
    private final String aprendiz;
    private final String fichaActual;
    private final String fichaPropuesta;
    private final String status;

    public PendingTransferResponseDTO(CsvPendingTransfer transfer) {
        this.idPendingTransfer = transfer.getIdPendingTransfer();
        this.idUser = transfer.getUser().getIdUser();
        this.fila = transfer.getSourceRowNumber();
        this.aprendiz = transfer.getUser().getFirstName() + " " + transfer.getUser().getLastName();
        this.fichaActual = transfer.getCurrentChip().getChipCode();
        this.fichaPropuesta = transfer.getProposedChip().getChipCode();
        this.status = transfer.getStatus().name();
    }
}
