package com.FaceLit.backend.academic.dto.response.academic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.FaceLit.backend.academic.model.academic.Instructor;
import com.FaceLit.backend.academic.model.academic.InstructorProgram;
import com.FaceLit.backend.academic.model.academic.Program;
import com.FaceLit.backend.academic.model.enums.InstructorType;
import com.FaceLit.backend.auth.model.enums.AccountStatus;
import com.FaceLit.backend.auth.model.security.User;

class InstructorResponseDTOTest {

    @Test
    void profileAuditUsesUserChangesButNotCredentialActivity() {
        var created = java.time.OffsetDateTime.parse("2026-10-20T10:00:00Z");
        var edited = created.plusDays(1);
        User user = new User();
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setCreatedAt(created); user.setUpdatedAt(edited);
        var credential = new com.FaceLit.backend.auth.model.security.Credential();
        credential.setUpdatedAt(edited.plusDays(1));
        user.setCredential(credential);
        Instructor instructor = new Instructor();
        instructor.setUser(user); instructor.setInstructorType(InstructorType.ESPECIFICO);
        instructor.setCreatedAt(created); instructor.setUpdatedAt(created);
        var response = new InstructorResponseDTO(instructor, List.of());
        assertEquals(created, response.getCreatedAt());
        assertEquals(edited, response.getUpdatedAt());
    }

    @Test
    void shouldExposeUserIdentityAndPrograms() {
        User user = new User();
        user.setIdUser(UUID.randomUUID());
        user.setDocumentNumber("1029384756");
        user.setFirstName("Laura");
        user.setLastName("Gómez");
        user.setAccountStatus(AccountStatus.ACTIVE);

        Instructor instructor = new Instructor();
        instructor.setIdInstructor(UUID.randomUUID());
        instructor.setUser(user);
        instructor.setInstructorType(InstructorType.ESPECIFICO);

        Program program = new Program();
        program.setIdProgram(UUID.randomUUID());
        program.setProgramName("ADSO");

        InstructorProgram instructorProgram = new InstructorProgram();
        instructorProgram.setInstructor(instructor);
        instructorProgram.setProgram(program);

        InstructorResponseDTO dto = new InstructorResponseDTO(instructor, List.of(instructorProgram));

        assertEquals("Laura", dto.getFirstName());
        assertEquals("Gómez", dto.getLastName());
        assertEquals("1029384756", dto.getDocument());
        assertEquals("ADSO", dto.getProgramNames().get(0));
    }
}
