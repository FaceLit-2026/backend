package com.FaceLit.backend.academic.dto.response.academic;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import com.FaceLit.backend.academic.model.academic.Instructor;
import com.FaceLit.backend.academic.model.academic.InstructorProgram;
import com.FaceLit.backend.auth.model.security.Credential;
import com.FaceLit.backend.auth.model.security.User;

import lombok.Getter;

@Getter
// Adapter: expone Instructor y sus programas sin serializar entidades JPA ni relaciones lazy.
public class InstructorResponseDTO {

    private final UUID idInstructor;
    private final UUID idUser;
    private final String firstName;
    private final String lastName;
    private final String document;
    private final String email;
    private final String instructorType;
    private final String status;
    private final String initialPassword;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;
    private final List<UUID> programIds;
    private final List<String> programNames;
    private final List<UUID> chipIds;

    public InstructorResponseDTO(Instructor instructor, List<InstructorProgram> instructorPrograms) {
        this(instructor, instructorPrograms, null, List.of());
    }

    public InstructorResponseDTO(Instructor instructor, List<InstructorProgram> instructorPrograms, String initialPassword) {
        this(instructor, instructorPrograms, initialPassword, List.of());
    }

    public InstructorResponseDTO(Instructor instructor, List<InstructorProgram> instructorPrograms, List<UUID> chipIds) {
        this(instructor, instructorPrograms, null, chipIds);
    }

    public InstructorResponseDTO(Instructor instructor, List<InstructorProgram> instructorPrograms, String initialPassword, List<UUID> chipIds) {
        User user = instructor.getUser();
        Credential credential = user.getCredential();

        this.idInstructor = instructor.getIdInstructor();
        this.idUser = user.getIdUser();
        this.firstName = user.getFirstName();
        this.lastName = user.getLastName();
        this.document = user.getDocumentNumber();
        this.email = credential != null ? credential.getEmail() : null;
        this.instructorType = instructor.getInstructorType().name();
        this.status = user.getAccountStatus().name();
        this.initialPassword = initialPassword;
        this.createdAt = instructor.getCreatedAt();
        this.updatedAt = com.FaceLit.backend.shared.model.AuditBase.latestUpdatedAt(instructor, user);
        this.programIds = instructorPrograms == null ? List.of() : instructorPrograms.stream()
                .map(ip -> ip.getProgram().getIdProgram())
                .collect(Collectors.toList());
        this.programNames = instructorPrograms == null ? List.of() : instructorPrograms.stream()
                .map(ip -> ip.getProgram().getProgramName())
                .collect(Collectors.toList());
        this.chipIds = chipIds == null ? List.of() : chipIds;
    }
}
