package com.FaceLit.backend.academic.service.serviceImpl.academic;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import com.FaceLit.backend.academic.dto.response.academic.CsvUploadResponseDTO;
import com.FaceLit.backend.academic.repository.ApprenticeRepository;
import com.FaceLit.backend.academic.repository.ChipRepository;
import com.FaceLit.backend.academic.repository.InstructorChipRepository;
import com.FaceLit.backend.academic.repository.InstructorProgramRepository;
import com.FaceLit.backend.academic.repository.InstructorRepository;
import com.FaceLit.backend.academic.repository.ProgramRepository;
import com.FaceLit.backend.academic.repository.UserChipRepository;
import com.FaceLit.backend.academic.model.academic.Program;
import com.FaceLit.backend.academic.model.enums.AcademicState;
import com.FaceLit.backend.auth.repository.security.CredentialRepository;
import com.FaceLit.backend.auth.repository.security.UserRepository;
import com.FaceLit.backend.auth.model.enums.AccountStatus;
import com.FaceLit.backend.auth.model.enums.CredentialStatus;
import com.FaceLit.backend.auth.model.enums.RoleName;
import com.FaceLit.backend.auth.model.roleandpermission.Role;
import com.FaceLit.backend.auth.model.security.Credential;
import com.FaceLit.backend.auth.model.security.User;
import com.FaceLit.backend.auth.repository.roleandpermission.RoleRepository;

@SpringBootTest
@ActiveProfiles("test")
class CsvAcademicServiceImplTest {
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.FaceLit.backend.shared.service.EmailService emailService;

    @Autowired
    private CsvAcademicServiceImpl csvAcademicService;

    @Autowired
    private ProgramRepository programRepository;

    @Autowired
    private ChipRepository chipRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CredentialRepository credentialRepository;

    @Autowired
    private InstructorRepository instructorRepository;

    @Autowired
    private InstructorProgramRepository instructorProgramRepository;

    @Autowired
    private InstructorChipRepository instructorChipRepository;

    @Autowired
    private ApprenticeRepository apprenticeRepository;

    @Autowired
    private UserChipRepository userChipRepository;

    @Autowired
    private RoleRepository roleRepository;

    @BeforeEach
    void seedRequiredRoles() {
        ensureRoleExists(RoleName.INSTRUCTOR);
        ensureRoleExists(RoleName.APPRENTICE);
    }

    private void ensureRoleExists(RoleName roleName) {
        if (roleRepository.findByNameRole(roleName).isEmpty()) {
            Role role = new Role();
            role.setNameRole(roleName);
            roleRepository.save(role);
        }
    }

    @Test
    void upload_shouldCreateProgramChipInstructorAndApprenticeWithPassword() throws Exception {
        String csv = """
            tipo,documento,nombre,apellido,correo,programa_codigo,ficha_codigo,instructor_tipo
            programa,,,,,ADSO,,
            ficha,,,,,ADSO,2825551,
            aprendiz,1002345678,Juan,Perez,juan.perez@correo.com,,2825551,
            instructor,1029384756,Laura,Gomez,laura.gomez@correo.com,ADSO,2825551,especifico
            """;

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "csv-upload.csv",
                "text/csv",
                csv.getBytes(StandardCharsets.UTF_8)
        );

        CsvUploadResponseDTO response = csvAcademicService.upload(file);

        var program = programRepository.findByProgramCodeIgnoreCase("ADSO").orElseThrow();
        var programId = program.getIdProgram();
        assertThat(program.getCreatedAt()).isNotNull();
        assertThat(program.getUpdatedAt()).isNotNull();
        assertThat(program.getState()).isNotNull();
        var apprenticeChip = chipRepository.findByChipCode("2825551").orElseThrow();
        assertThat(userRepository.findByDocumentNumber("1002345678")).isPresent();
        var instructorUser = userRepository.findByDocumentNumber("1029384756").orElseThrow();
        assertThat(instructorRepository.findByUser_IdUser(instructorUser.getIdUser())).isPresent();
        assertThat(instructorProgramRepository.findByInstructor_IdInstructor(
                instructorRepository.findByUser_IdUser(instructorUser.getIdUser()).orElseThrow().getIdInstructor()))
                .anySatisfy(relation -> assertThat(relation.getProgram().getIdProgram()).isEqualTo(programId));
        var instructor = instructorRepository.findByUser_IdUser(instructorUser.getIdUser()).orElseThrow();
        assertThat(instructorChipRepository.findByInstructor_IdInstructorAndActiveTrue(instructor.getIdInstructor()))
                .anySatisfy(relation -> assertThat(relation.getChip().getIdChip()).isEqualTo(apprenticeChip.getIdChip()));
        var apprenticeUser = userRepository.findByDocumentNumber("1002345678").orElseThrow();
        assertThat(apprenticeRepository.findByUser_IdUser(apprenticeUser.getIdUser()))
                .isPresent();
        assertThat(userChipRepository.findByApprentice_User_IdUserAndState(apprenticeUser.getIdUser(), AcademicState.ACTIVE))
                .isPresent()
                .get()
                .satisfies(userChip -> assertThat(userChip.getChip().getIdChip()).isEqualTo(apprenticeChip.getIdChip()));
        assertThat(credentialRepository.findByUser_DocumentNumber("1002345678")).isPresent();
        assertThat(response.getContrasenasGeneradas())
                .anySatisfy(item -> {
                    assertThat(item.documento()).isEqualTo("1002345678");
                    assertThat(item.contrasenaTemporal()).isNotBlank();
                    assertThat(item.contrasenaTemporal()).hasSizeGreaterThanOrEqualTo(8);
                });
        assertThat(response.getCreados()).hasSizeGreaterThanOrEqualTo(4);
    }

    @Test
    void upload_shouldCreateApprenticeRowForExistingUserApp() throws Exception {
        String suffix = String.valueOf(Math.abs(UUID.randomUUID().hashCode()));
        suffix = (suffix + "0000000").substring(0, 7);
        String document = "77" + suffix;
        User existingUser = new User();
        existingUser.setDocumentNumber(document);
        existingUser.setFirstName("Ana");
        existingUser.setLastName("Lopez");
        existingUser.setAccountStatus(AccountStatus.ACTIVE);
        existingUser = userRepository.save(existingUser);

        Credential credential = new Credential();
        credential.setUser(existingUser);
        credential.setEmail("ana." + suffix.toLowerCase() + "@example.com");
        credential.setPassword("encoded-password");
        credential.setCredentialStatus(CredentialStatus.ACTIVE);
        credential.setFailedAttempts(0);
        credentialRepository.save(credential);

        String programCode = "TST" + suffix.substring(0, 4);
        String chipCode = "3" + suffix.substring(0, 6);
        String csv = """
                tipo,documento,nombre,apellido,correo,programa_codigo,ficha_codigo,instructor_tipo
                programa,,Programa Test,,,%s,,
                ficha,,,,,%s,%s,
                aprendiz,%s,Ana,Lopez,ana.%s@example.com,,%s,
                """.formatted(programCode, programCode, chipCode, document, suffix.toLowerCase(), chipCode);

        CsvUploadResponseDTO response = csvAcademicService.upload(csvFile(csv));
        var chip = chipRepository.findByChipCode(chipCode).orElseThrow();

        assertThat(response.getErroresDeReferencia()).isEmpty();
        assertThat(apprenticeRepository.findByUser_IdUser(existingUser.getIdUser()))
                .isPresent();
        assertThat(userChipRepository.findByApprentice_User_IdUserAndState(existingUser.getIdUser(), AcademicState.ACTIVE))
                .isPresent()
                .get()
                .satisfies(userChip -> assertThat(userChip.getChip().getIdChip()).isEqualTo(chip.getIdChip()));
    }

    @Test
    void upload_shouldPersistProgramWhenCsvHasUtf8Bom() throws Exception {
        String code = "Q" + UUID.randomUUID().toString().replace("-", "").substring(0, 7).toUpperCase();
        String csv = "\uFEFFtipo,documento,nombre,apellido,correo,programa_codigo,ficha_codigo,instructor_tipo\r\n"
                + "programa,," + "Programa de prueba,,," + code + ",,\r\n";
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "csv-bom.csv",
                "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));

        CsvUploadResponseDTO response = csvAcademicService.upload(file);

        assertThat(response.getCreados()).anySatisfy(row -> assertThat(row.detalle()).contains(code));
        assertThat(programRepository.findByProgramCodeIgnoreCase(code)).isPresent();
    }

    @Test
    void template_shouldIncludeCompleteExamplesForEveryRowType() {
        String template = new String(csvAcademicService.template(), StandardCharsets.UTF_8);

        assertThat(template).contains(
                "tipo,documento,nombre,apellido,correo,programa_codigo,ficha_codigo,instructor_tipo",
                "programa,,Análisis y Desarrollo de Software,,,ADSO,,",
                "ficha,,,,,ADSO,2825551,",
                "aprendiz,100234,Juan,Perez,juan.perez@correo.com,,2825551,",
                "instructor,1029384756,Laura,Gomez,laura.gomez@correo.com,ADSO,2825551,especifico",
                "instructor,1050607080,Carlos,Ruiz,carlos.ruiz@correo.com,,,transversal");
    }

    @Test
    void upload_shouldResolveExistingProgramForSpecificInstructor() throws Exception {
        String code = "P" + UUID.randomUUID().toString().replace("-", "").substring(0, 7).toUpperCase();
        Program program = new Program();
        program.setProgramCode(code);
        program.setProgramName("Programa existente");
        program.setState(AcademicState.ACTIVE);
        program = programRepository.save(program);
        var programId = program.getIdProgram();
        String csv = """
                tipo,documento,nombre,apellido,correo,programa_codigo,ficha_codigo,instructor_tipo
                instructor,1029384757,Laura,Gomez,laura.gomez@example.com,%s,,especifico
                """.formatted(code);

        CsvUploadResponseDTO response = csvAcademicService.upload(csvFile(csv));

        assertThat(response.getErroresDeReferencia()).isEmpty();
        assertThat(response.getCreados()).anySatisfy(row -> assertThat(row.tipo()).isEqualTo("instructor"));
        assertThat(instructorProgramRepository.findAll())
                .anySatisfy(relation -> assertThat(relation.getProgram().getIdProgram()).isEqualTo(programId));
    }

    @Test
    void upload_shouldReportMissingSpecificInstructorProgramWithoutCreatingRecords() throws Exception {
        String csv = """
                tipo,documento,nombre,apellido,correo,programa_codigo,ficha_codigo,instructor_tipo
                instructor,1029384758,Laura,Gomez,laura.gomez@example.com,NOEXISTE,,especifico
                """;

        CsvUploadResponseDTO response = csvAcademicService.upload(csvFile(csv));

        assertThat(response.getErroresDeReferencia()).hasSize(1);
        assertThat(response.getCreados()).isEmpty();
        assertThat(userRepository.findByDocumentNumber("1029384758")).isEmpty();
    }

    private MockMultipartFile csvFile(String csv) {
        return new MockMultipartFile("file", "csv-upload.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
    }
}
