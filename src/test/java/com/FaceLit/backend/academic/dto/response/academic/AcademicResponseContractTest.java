package com.FaceLit.backend.academic.dto.response.academic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.FaceLit.backend.academic.model.academic.Chip;
import com.FaceLit.backend.academic.model.academic.Instructor;
import com.FaceLit.backend.academic.model.academic.InstructorProgram;
import com.FaceLit.backend.academic.model.academic.Program;
import com.FaceLit.backend.academic.model.enums.AcademicState;
import com.FaceLit.backend.academic.model.enums.InstructorType;
import com.FaceLit.backend.auth.model.enums.AccountStatus;
import com.FaceLit.backend.auth.model.enums.CredentialStatus;
import com.FaceLit.backend.auth.model.security.Credential;
import com.FaceLit.backend.auth.model.security.User;

class AcademicResponseContractTest {

    @Test
    void instructorResponseIncludesPersonAndProgramData() {
        UUID idProgram = UUID.randomUUID();
        Program program = new Program();
        program.setIdProgram(idProgram);
        program.setProgramName("Desarrollo de software");
        program.setProgramCode("ADS");
        program.setState(AcademicState.ACTIVE);

        User user = new User();
        user.setIdUser(UUID.randomUUID());
        user.setDocumentNumber("1234567890");
        user.setFirstName("Ana");
        user.setLastName("Pérez");
        user.setAccountStatus(AccountStatus.ACTIVE);

        Credential credential = new Credential();
        credential.setEmail("ana@correo.com");
        credential.setPassword("hash");
        credential.setCredentialStatus(CredentialStatus.ACTIVE);
        credential.setFailedAttempts(0);
        credential.setUser(user);
        user.setCredential(credential);

        Instructor instructor = new Instructor();
        instructor.setIdInstructor(UUID.randomUUID());
        instructor.setUser(user);
        instructor.setInstructorType(InstructorType.TRANSVERSAL);

        InstructorProgram ip = new InstructorProgram();
        ip.setInstructor(instructor);
        ip.setProgram(program);

        InstructorResponseDTO dto = new InstructorResponseDTO(instructor, List.of(ip), "Temp123");

        assertEquals("Ana", dto.getFirstName());
        assertEquals("Pérez", dto.getLastName());
        assertEquals("1234567890", dto.getDocument());
        assertEquals("ana@correo.com", dto.getEmail());
        assertEquals("Temp123", dto.getInitialPassword());
        assertEquals(List.of(idProgram), dto.getProgramIds());
        assertEquals(List.of("Desarrollo de software"), dto.getProgramNames());
    }

    @Test
    void programResponseIncludesChipSummary() {
        Program program = new Program();
        program.setIdProgram(UUID.randomUUID());
        program.setProgramName("Radiología");
        program.setProgramCode("RAD");
        program.setState(AcademicState.ACTIVE);
        OffsetDateTime createdAt = OffsetDateTime.now().minusHours(1);
        OffsetDateTime updatedAt = OffsetDateTime.now();
        program.setCreatedAt(createdAt);
        program.setUpdatedAt(updatedAt);

        Chip chip = new Chip();
        chip.setIdChip(UUID.randomUUID());
        chip.setProgram(program);
        chip.setChipCode("RAD-01");
        chip.setState(AcademicState.ACTIVE);

        ProgramResponseDTO dto = new ProgramResponseDTO(program, List.of(chip));

        assertNotNull(dto.getChips());
        assertEquals(1, dto.getChipIds().size());
        assertEquals("RAD-01", dto.getChipCodes().get(0));
        assertEquals(createdAt, dto.getCreatedAt());
        assertEquals(updatedAt, dto.getUpdatedAt());
    }
}
