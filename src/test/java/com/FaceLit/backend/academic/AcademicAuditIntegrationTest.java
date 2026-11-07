package com.FaceLit.backend.academic;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.FaceLit.backend.academic.dto.request.academic.*;
import com.FaceLit.backend.academic.dto.response.academic.*;
import com.FaceLit.backend.academic.model.enums.InstructorType;
import com.FaceLit.backend.academic.repository.*;
import com.FaceLit.backend.academic.service.serviceImpl.academic.*;
import com.FaceLit.backend.auth.model.enums.RoleName;
import com.FaceLit.backend.auth.model.roleandpermission.Role;
import com.FaceLit.backend.auth.repository.roleandpermission.RoleRepository;
import com.FaceLit.backend.shared.service.EmailService;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class AcademicAuditIntegrationTest {
    @MockitoBean private EmailService emailService;
    @Autowired private CsvAcademicServiceImpl csv;
    @Autowired private UserChipServiceImpl learners;
    @Autowired private InstructorServiceImpl instructors;
    @Autowired private ProgramServiceImpl programs;
    @Autowired private ChipServiceImpl chips;
    @Autowired private ChipRepository chipRepository;
    @Autowired private ProgramRepository programRepository;
    @Autowired private InstructorRepository instructorRepository;
    @Autowired private RoleRepository roles;
    @Autowired private EntityManager em;

    private String code, chipCode, learnerDocument, instructorDocument, suffix;
    private UUID chipId, programId, learnerId, instructorId;

    @BeforeEach
    void createFixture() throws Exception {
        for (RoleName name : List.of(RoleName.APPRENTICE, RoleName.INSTRUCTOR)) {
            if (roles.findByNameRole(name).isEmpty()) {
                Role role = new Role(); role.setNameRole(name); roles.save(role);
            }
        }
        suffix = UUID.randomUUID().toString().replace("-", "");
        code = "AUD" + suffix.substring(0, 7);
        chipCode = String.valueOf(ThreadLocalRandom.current().nextInt(1000000, 9999999));
        learnerDocument = String.valueOf(ThreadLocalRandom.current().nextLong(1000000000L, 9000000000L));
        instructorDocument = String.valueOf(Long.parseLong(learnerDocument) + 1);
        upload("""
                tipo,documento,nombre,apellido,correo,programa_codigo,ficha_codigo,instructor_tipo
                programa,,Programa auditoria,,,%s,,
                ficha,,,,,%s,%s,
                aprendiz,%s,Ana,Lopez,learner.%s@example.invalid,,%s,
                instructor,%s,Laura,Gomez,instructor.%s@example.invalid,%s,%s,especifico
                """.formatted(code, code, chipCode, learnerDocument, suffix, chipCode,
                        instructorDocument, suffix, code, chipCode));
        em.flush(); em.clear();
        chipId = chipRepository.findByChipCode(chipCode).orElseThrow().getIdChip();
        programId = programRepository.findByProgramCodeIgnoreCase(code).orElseThrow().getIdProgram();
        learnerId = learner().getIdUser();
        instructorId = instructorRepository.findAll().stream()
                .filter(i -> i.getUser().getDocumentNumber().equals(instructorDocument))
                .findFirst().orElseThrow().getIdInstructor();
    }

    private UserChipResponseDTO learner() { return learners.findApprenticesByChip(chipId).get(0); }
    private void upload(String data) throws Exception {
        var result = csv.upload(new MockMultipartFile("file", "audit.csv", "text/csv", data.getBytes(StandardCharsets.UTF_8)));
        assertThat(result.getErroresDeReferencia()).isEmpty();
        assertThat(result.getInconsistenciasBloqueadas()).isEmpty();
    }
    private UserChipRequestDTO learnerRequest(String name, String email) {
        var request = new UserChipRequestDTO();
        request.setDocumento(learnerDocument); request.setNombre(name); request.setApellido("Lopez"); request.setCorreo(email);
        return request;
    }

    @Test
    void manualLearnerChangeReturnsFreshAuditBeforeReturningAndPreservesCreation() {
        var before = learner();
        var result = learners.updateApprentice(learnerId, learnerRequest("Andrea", before.getEmail()));
        assertThat(result.getFirstName()).isEqualTo("Andrea");
        assertThat(result.getUpdatedAt().toInstant()).isAfter(before.getUpdatedAt().toInstant());
        assertThat(result.getCreatedAt()).isEqualTo(before.getCreatedAt());
        em.flush(); em.clear();
        assertThat(learner().getUpdatedAt().toInstant()).isCloseTo(result.getUpdatedAt().toInstant(),
                org.assertj.core.api.Assertions.within(1, java.time.temporal.ChronoUnit.MILLIS));
    }

    @Test
    void emailOnlyChangesUpdateProfileAuditWithoutUsingCredentialLoginAudit() {
        var before = learner();
        var result = learners.updateApprentice(learnerId, learnerRequest(before.getFirstName(), "new." + suffix + "@example.invalid"));
        assertThat(result.getEmail()).startsWith("new.");
        assertThat(result.getUpdatedAt().toInstant()).isAfter(before.getUpdatedAt().toInstant());
        assertThat(result.getCreatedAt()).isEqualTo(before.getCreatedAt());
    }

    @Test
    void csvProfileUpdatesAreVisibleOnLearnerAndInstructorResponses() throws Exception {
        var before = learner();
        var instructorBefore = instructors.findById(instructorId);
        upload("""
                tipo,documento,nombre,apellido,correo,programa_codigo,ficha_codigo,instructor_tipo
                aprendiz,%s,Andrea,Lopez,learner.%s@example.invalid,,%s,
                instructor,%s,Lucia,Gomez,instructor.%s@example.invalid,%s,%s,especifico
                """.formatted(learnerDocument, suffix, chipCode, instructorDocument, suffix, code, chipCode));
        em.flush(); em.clear();
        assertThat(learner().getFirstName()).isEqualTo("Andrea");
        assertThat(learner().getUpdatedAt().toInstant()).isAfter(before.getUpdatedAt().toInstant());
        assertThat(learner().getCreatedAt()).isEqualTo(before.getCreatedAt());
        var after = instructors.findById(instructorId);
        assertThat(after.getFirstName()).isEqualTo("Lucia");
        assertThat(after.getUpdatedAt().toInstant()).isAfter(instructorBefore.getUpdatedAt().toInstant());
        assertThat(after.getCreatedAt()).isEqualTo(instructorBefore.getCreatedAt());
    }

    @Test
    void savingAnUnchangedCsvDoesNotInventAModification() throws Exception {
        Instant before = learner().getUpdatedAt().toInstant();
        upload("""
                tipo,documento,nombre,apellido,correo,programa_codigo,ficha_codigo,instructor_tipo
                aprendiz,%s,Ana,Lopez,learner.%s@example.invalid,,%s,
                """.formatted(learnerDocument, suffix, chipCode));
        em.flush(); em.clear();
        assertThat(learner().getUpdatedAt().toInstant()).isEqualTo(before);
    }

    @Test
    void programAndChipUpdatesReturnPersistedModificationTimes() {
        var programBefore = programs.findById(programId);
        var programRequest = new ProgramRequestDTO();
        programRequest.setProgramCode(code); programRequest.setProgramName("Programa actualizado");
        var programAfter = programs.update(programId, programRequest);
        assertThat(programAfter.getUpdatedAt().toInstant()).isAfter(programBefore.getUpdatedAt().toInstant());
        assertThat(programAfter.getCreatedAt()).isEqualTo(programBefore.getCreatedAt());
        var chipBefore = chips.findById(chipId);
        var chipRequest = new ChipRequestDTO();
        chipRequest.setIdProgram(programId);
        chipRequest.setChipCode(chipCode.equals("9999999") ? "9999998" : "9999999");
        var chipAfter = chips.update(chipId, chipRequest);
        assertThat(chipAfter.getUpdatedAt().toInstant()).isAfter(chipBefore.getUpdatedAt().toInstant());
        assertThat(chipAfter.getCreatedAt()).isEqualTo(chipBefore.getCreatedAt());
    }

    @Test
    void manualInstructorProfileChangeReturnsCurrentModificationTime() {
        var before = instructors.findById(instructorId);
        var request = new InstructorRequestDTO();
        request.setDocumento(instructorDocument); request.setNombre("Lucia"); request.setApellido("Gomez");
        request.setCorreo(before.getEmail()); request.setInstructorType(InstructorType.ESPECIFICO); request.setProgramIds(List.of(programId));
        var result = instructors.update(instructorId, request);
        assertThat(result.getUpdatedAt().toInstant()).isAfter(before.getUpdatedAt().toInstant());
        assertThat(result.getCreatedAt()).isEqualTo(before.getCreatedAt());
    }
}
