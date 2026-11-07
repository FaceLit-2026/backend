package com.FaceLit.backend.academic.dto.response.academic;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.FaceLit.backend.academic.model.academic.UserChip;
import com.FaceLit.backend.auth.model.security.Credential;
import com.FaceLit.backend.auth.model.security.User;

import lombok.Getter;

@Getter
// Adapter: convierte la relación UserChip a un contrato estable para el frontend.
public class UserChipResponseDTO {

    private final UUID idUserChip;
    private final UUID idUser;
    private final UUID idChip;
    private final String chipCode;
    private final UUID idProgram;
    private final String programName;
    private final String programCode;
    private final String programState;
    private final String chipState;
    private final String firstName;
    private final String lastName;
    private final String document;
    private final String email;
    private final String state;
    private final String accountStatus;
    private final String initialPassword;
    private final OffsetDateTime assignmentDate;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;

    public UserChipResponseDTO(UserChip userChip) {
        this(userChip, null);
    }

    public UserChipResponseDTO(UserChip userChip, String initialPassword) {
        User user = userChip.getUser();
        Credential credential = user.getCredential();

        this.idUserChip = userChip.getIdUserChip();
        this.idUser = user.getIdUser();
        this.idChip = userChip.getChip().getIdChip();
        this.chipCode = userChip.getChip().getChipCode();
        this.idProgram = userChip.getChip().getProgram().getIdProgram();
        this.programName = userChip.getChip().getProgram().getProgramName();
        this.programCode = userChip.getChip().getProgram().getProgramCode();
        this.programState = userChip.getChip().getProgram().getState().name();
        this.chipState = userChip.getChip().getState().name();
        this.firstName = user.getFirstName();
        this.lastName = user.getLastName();
        this.document = user.getDocumentNumber();
        this.email = credential != null ? credential.getEmail() : null;
        this.state = userChip.getState().name();
        this.accountStatus = user.getAccountStatus() == null ? "INACTIVE" : user.getAccountStatus().name();
        this.initialPassword = initialPassword;
        this.assignmentDate = userChip.getAssignmentDate();
        this.createdAt = userChip.getCreatedAt();
        this.updatedAt = com.FaceLit.backend.shared.model.AuditBase.latestUpdatedAt(userChip, user);
    }
}
