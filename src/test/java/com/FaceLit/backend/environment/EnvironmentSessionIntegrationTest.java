package com.FaceLit.backend.environment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.FaceLit.backend.academic.model.academic.*;
import com.FaceLit.backend.academic.model.enums.AcademicState;
import com.FaceLit.backend.academic.model.enums.InstructorType;
import com.FaceLit.backend.auth.model.enums.AccountStatus;
import com.FaceLit.backend.auth.model.security.User;
import com.FaceLit.backend.environment.model.RecordEnvironment;
import com.FaceLit.backend.environment.repository.RecordEnvironmentRepository;
import com.FaceLit.backend.shared.service.EmailService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/** Real controller, authorization, services, repositories and SQL; no camera or SMTP. */
@SpringBootTest(properties = {
        "spring.datasource.url=${FACELIT_SESSION_TEST_DB:jdbc:h2:mem:session_flow;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE}",
        "spring.datasource.driver-class-name=${FACELIT_SESSION_TEST_DRIVER:org.h2.Driver}",
        "spring.datasource.username=${FACELIT_SESSION_TEST_USER:sa}",
        "spring.datasource.password=${FACELIT_SESSION_TEST_PASSWORD:}",
        "spring.jpa.hibernate.ddl-auto=${FACELIT_SESSION_TEST_DDL:create-drop}",
        "spring.jpa.properties.hibernate.dialect=${FACELIT_SESSION_TEST_DIALECT:org.hibernate.dialect.H2Dialect}",
        "spring.jpa.show-sql=false",
        "spring.mail.username=test@example.invalid", "spring.mail.password=unused",
        "jwt.secret=session-integration-test-secret-at-least-thirty-two-characters"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EnvironmentSessionIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired RecordEnvironmentRepository sessions;
    @MockitoBean EmailService email;
    private Instructor teacher;
    private Chip chip;

    @BeforeEach void fixture() {
        // H2 DDL commits implicitly: create the fixture-only table before any business rows.
        if (System.getenv("FACELIT_SESSION_TEST_DB") == null) {
            jdbc.execute("CREATE TABLE IF NOT EXISTS facialrecognition.user_face (id_user_face UUID PRIMARY KEY, id_apprentice UUID, status VARCHAR(20), deleted_at TIMESTAMP WITH TIME ZONE)");
        }
        Program program = new Program(); program.setProgramCode(unique()); program.setProgramName("Test program " + unique());
        program.setState(AcademicState.ACTIVE); em.persist(program);
        chip = new Chip(); chip.setChipCode(unique()); chip.setProgram(program); chip.setState(AcademicState.ACTIVE); em.persist(chip);
        teacher = instructor(InstructorType.ESPECIFICO);
        InstructorProgram link = new InstructorProgram(); link.setInstructor(teacher); link.setProgram(program); em.persist(link);
        Apprentice learner = new Apprentice(); learner.setUser(user()); em.persist(learner);
        UserChip membership = new UserChip(); membership.setApprentice(learner); membership.setChip(chip);
        membership.setState(AcademicState.ACTIVE); em.persist(membership); em.flush();
        // H2 has no user_face entity; PostgreSQL runs against the actual Liquibase table.
        if (System.getenv("FACELIT_SESSION_TEST_DB") == null) {
            jdbc.update("INSERT INTO facialrecognition.user_face(id_user_face,id_apprentice,status) VALUES (?,?, 'ACTIVE')", UUID.randomUUID(), learner.getIdApprentice());
        } else {
            jdbc.update("INSERT INTO facialrecognition.user_face(id_user_face,id_apprentice,status,embedding,embedding_dimension) VALUES (?,?, 'ACTIVE','[1,0,0]',3)", UUID.randomUUID(), learner.getIdApprentice());
        }
    }

    @Test void setupPersistsAndCanBeRecoveredWithoutAnOptionalReplacement() throws Exception {
        UUID room = room();
        JsonNode created = create(room, teacher, teacher);
        UUID id = UUID.fromString(created.path("idRecordEnvironment").asText());
        em.flush(); em.clear();
        assertThat(sessions.findCurrentActiveSessionsByInstructor(teacher.getUser().getIdUser(),
                org.springframework.data.domain.PageRequest.of(0,1)))
                .extracting(RecordEnvironment::getIdRecordEnvironment).containsExactly(id);
        JsonNode restored = json.readTree(mvc.perform(get("/api/environment/sessions/active").with(as(teacher)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsString());
        assertThat(restored.path("idRecordEnvironment").asText()).isEqualTo(id.toString());
        assertThat(restored.path("idInstructorInCharge").isNull()).isTrue();
        assertThat(restored.path("environmentName").asText()).isEqualTo("Room " + roomName);
        assertThat(restored.path("idChip").asText()).isEqualTo(chip.getIdChip().toString());
        // SQL timestamps round to microseconds; compare instants within that storage precision.
        assertThat(OffsetDateTime.parse(restored.path("entryTime").asText()).toInstant())
                .isCloseTo(OffsetDateTime.parse(created.path("entryTime").asText()).toInstant(),
                        org.assertj.core.api.Assertions.within(1, java.time.temporal.ChronoUnit.MICROS));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM environment.record_environment r JOIN environment.environment e ON e.id_environment=r.id_environment JOIN academic.chip c ON c.id_chip=r.id_chip JOIN academic.instructor i ON i.id_instructor=r.id_instructor_scheduled WHERE r.id_record_environment=? AND e.id_environment=? AND c.id_chip=? AND i.id_instructor=?", Integer.class, id, room, chip.getIdChip(), teacher.getIdInstructor())).isEqualTo(1);
        assertThat(sessions.findDistinctChipIdsByInstructor(teacher.getIdInstructor())).contains(chip.getIdChip());
    }

    @Test void latestSessionIsRecoveredWhenSeveralAreStillActive() throws Exception {
        UUID room = room();
        JsonNode first = create(room, teacher, teacher);
        sessions.findById(UUID.fromString(first.path("idRecordEnvironment").asText())).orElseThrow()
                .setEntryTime(OffsetDateTime.now().minusMinutes(1));
        em.flush();
        JsonNode second = create(room, teacher, teacher);
        mvc.perform(get("/api/environment/sessions/active").with(as(teacher))).andExpect(status().isOk())
                .andExpect(jsonPath("$.idRecordEnvironment").value(second.path("idRecordEnvironment").asText()));
        assertThat(first.path("idRecordEnvironment")).isNotEqualTo(second.path("idRecordEnvironment"));
    }

    @Test void ownerCanUpdateAnUnusedSessionAndRetrieveTheSameRecordWithNewContext() throws Exception {
        JsonNode created = create(room(), teacher, teacher);
        UUID secondRoom = room();
        String id = created.path("idRecordEnvironment").asText();
        mvc.perform(put("/api/environment/sessions/" + id).with(as(teacher)).contentType("application/json")
                .content(json.writeValueAsString(payload(secondRoom,teacher))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.idRecordEnvironment").value(id));
        em.flush(); em.clear();
        mvc.perform(get("/api/environment/sessions/active").with(as(teacher))).andExpect(status().isOk())
                .andExpect(jsonPath("$.idEnvironment").value(secondRoom.toString()));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM environment.record_environment WHERE id_record_environment=? AND id_environment=?",
                Integer.class,UUID.fromString(id),secondRoom)).isEqualTo(1);
    }

    @Test void replacementKeepsScheduledAndActualInstructorAndRejectsUnrelatedUpdates() throws Exception {
        Instructor replacement = instructor(InstructorType.TRANSVERSAL);
        UUID room = room(); JsonNode created = create(room, teacher, replacement);
        assertThat(created.path("idInstructorScheduled").asText()).isEqualTo(teacher.getIdInstructor().toString());
        assertThat(created.path("idInstructorInCharge").asText()).isEqualTo(replacement.getIdInstructor().toString());
        mvc.perform(get("/api/environment/sessions/active").with(as(replacement))).andExpect(status().isOk());
        Instructor stranger = instructor(InstructorType.TRANSVERSAL);
        mvc.perform(put("/api/environment/sessions/" + created.path("idRecordEnvironment").asText())
                .with(as(stranger)).contentType("application/json").content(json.writeValueAsString(payload(room, teacher))))
                .andExpect(status().isForbidden());
    }

    @Test void directRequestCannotChooseAChipOutsideTheInstructorsProgram() throws Exception {
        Instructor unrelated = instructor(InstructorType.ESPECIFICO);
        UUID room = room();
        mvc.perform(post("/api/environment/sessions").with(as(unrelated)).contentType("application/json")
                .content(json.writeValueAsString(payload(room, teacher)))).andExpect(status().isForbidden());
        mvc.perform(get("/api/environment/chips-for-instructor/" + teacher.getIdInstructor()).with(as(unrelated)))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test void apprenticeAndCoordinatorCannotConfigureInstructorSessionsAndAnonymousGets401() throws Exception {
        for (String role : List.of("APPRENTICE")) {
            mvc.perform(get("/api/environment/sessions/active").with(as(UUID.randomUUID(),role)))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/environment/environments").with(as(UUID.randomUUID(), role))
                    .contentType("application/json").content("{\"environmentName\":\"Denied\"}"))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/environment/sessions").with(as(UUID.randomUUID(), role))
                    .contentType("application/json").content("{}")) .andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/environment/environments").with(as(UUID.randomUUID(), "COORDINATOR")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/environment/environments").with(as(UUID.randomUUID(), "COORDINATOR"))
                .contentType("application/json").content("{\"environmentName\":\"Coordinator room\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/environment/sessions").with(as(UUID.randomUUID(), "COORDINATOR"))
                .contentType("application/json").content("{}")) .andExpect(status().isForbidden());
        mvc.perform(get("/api/environment/sessions/active")).andExpect(status().isUnauthorized());
    }

    @Test void coordinatorCanManageEnvironmentAndFichaAssignment() throws Exception {
        UUID actor = UUID.randomUUID();
        JsonNode created = json.readTree(mvc.perform(post("/api/environment/environments")
                .with(as(actor, "COORDINATOR"))
                .contentType("application/json")
                .content("{\"environmentName\":\"Coordinator managed room\",\"capacity\":2}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String environmentId = created.path("idEnvironment").asText();

        mvc.perform(patch("/api/environment/environments/" + environmentId)
                .with(as(actor, "COORDINATOR"))
                .contentType("application/json")
                .content("{\"environmentName\":\"Coordinator managed room updated\",\"capacity\":3}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.environmentName").value("Coordinator managed room updated"));
        em.flush(); em.clear();
        assertThat(jdbc.queryForObject("select environment_name from environment.environment where id_environment = ?",
                String.class, UUID.fromString(environmentId))).isEqualTo("Coordinator managed room updated");

        mvc.perform(post("/api/environment/environments/" + environmentId + "/fichas")
                .with(as(actor, "COORDINATOR"))
                .contentType("application/json")
                .content("{\"idChip\":\"" + chip.getIdChip() + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fichaCode").value(chip.getChipCode()));
        em.flush(); em.clear();
        assertThat(jdbc.queryForObject("select count(*) from environment.chip_environment where id_environment = ? and id_chip = ? and state = 'ACTIVE' and deleted_at is null",
                Integer.class, UUID.fromString(environmentId), chip.getIdChip())).isEqualTo(1);

        mvc.perform(get("/api/environment/environments").param("idChip", chip.getIdChip().toString())
                .with(as(actor, "INSTRUCTOR")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].idEnvironment").value(environmentId));
        mvc.perform(get("/api/environment/environments").param("idChip", UUID.randomUUID().toString())
                .with(as(actor, "INSTRUCTOR")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(delete("/api/environment/environments/" + environmentId + "/fichas/" + chip.getIdChip())
                .with(as(actor, "COORDINATOR"))).andExpect(status().isNoContent());
        mvc.perform(delete("/api/environment/environments/" + environmentId)
                .with(as(actor, "COORDINATOR"))).andExpect(status().isNoContent());
        em.flush(); em.clear();
        assertThat(jdbc.queryForObject("select state from environment.environment where id_environment = ?",
                String.class, UUID.fromString(environmentId))).isEqualTo("INACTIVE");
        mvc.perform(get("/api/environment/environments")
                .param("includeInactive", "true")
                .with(as(actor, "COORDINATOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].idEnvironment").value(environmentId))
                .andExpect(jsonPath("$[0].state").value("INACTIVE"));
        mvc.perform(patch("/api/environment/environments/" + environmentId)
                .with(as(actor, "COORDINATOR"))
                .contentType("application/json")
                .content("{\"state\":\"ACTIVE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("ACTIVE"));
        em.flush(); em.clear();
        assertThat(jdbc.queryForObject("select state from environment.environment where id_environment = ? and deleted_at is null",
                String.class, UUID.fromString(environmentId))).isEqualTo("ACTIVE");
    }

    @Test void deletedSessionsAreNotRestoredOrUsedAsAssignments() throws Exception {
        JsonNode created = create(room(), teacher, teacher);
        UUID id = UUID.fromString(created.path("idRecordEnvironment").asText());
        sessions.findById(id).orElseThrow().setDeletedAt(OffsetDateTime.now()); em.flush(); em.clear();
        mvc.perform(get("/api/environment/sessions/active").with(as(teacher))).andExpect(status().isNoContent());
        assertThat(sessions.findDistinctChipIdsByInstructor(teacher.getIdInstructor())).isEmpty();
    }

    @Test void attendanceRemainsLinkedAndConfigurationCannotRewriteItsContext() throws Exception {
        UUID room = room(); JsonNode created = create(room, teacher, teacher);
        UUID id = UUID.fromString(created.path("idRecordEnvironment").asText());
        RecordEnvironment record = sessions.findById(id).orElseThrow();
        Apprentice learner = em.createQuery("select a from Apprentice a join UserChip link on link.apprentice=a where link.chip.idChip=:id", Apprentice.class)
                .setParameter("id", chip.getIdChip()).getSingleResult();
        String body = json.writeValueAsString(Map.of("idRecordEnvironment", id, "idDevice", record.getDevice().getIdDevice(),
                "idApprentice", learner.getIdApprentice(), "recognitionResult", "TEST", "origin", "PC"));
        JsonNode event = json.readTree(mvc.perform(post("/api/facial/events").with(as(teacher))
                .contentType("application/json").content(body)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        UUID eventId = UUID.fromString(event.path("idFacialEvent").asText());
        mvc.perform(get("/api/facial/events/attendance-status").with(as(teacher))
                .param("idRecordEnvironment",id.toString()).param("idApprentice",learner.getIdApprentice().toString()))
                .andExpect(status().isOk());
        if (System.getenv("FACELIT_SESSION_TEST_DB") != null) {
            String date = java.time.LocalDate.now(java.time.ZoneId.of("America/Bogota")).toString();
            mvc.perform(get("/api/facial/events/attendance-matrix").with(as(teacher))
                    .param("idChip",chip.getIdChip().toString()).param("dateFrom",date).param("dateTo",date))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.sessions[0].idRecordEnvironment").value(id.toString()))
                    .andExpect(jsonPath("$.sessions[0].environmentName").value("Room " + roomName))
                    .andExpect(jsonPath("$.learners[0].days[0].idFacialEvent").value(eventId.toString()));
        }
        mvc.perform(put("/api/environment/sessions/" + id).with(as(teacher)).contentType("application/json")
                .content(json.writeValueAsString(payload(room, teacher)))).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM facialrecognition.facial_event f JOIN environment.record_environment r ON r.id_record_environment=f.id_record_environment WHERE f.id_facial_event=? AND r.id_chip=? AND r.id_environment=? AND r.id_instructor_scheduled=?", Integer.class, eventId, chip.getIdChip(), room, teacher.getIdInstructor())).isEqualTo(1);
    }

    private String roomName;
    private UUID room() throws Exception {
        roomName = unique();
        String body = json.writeValueAsString(Map.of("environmentName", "  Room   " + roomName + "  "));
        String response = mvc.perform(post("/api/environment/environments").with(as(teacher)).contentType("application/json").content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(json.readTree(response).path("idEnvironment").asText());
        mvc.perform(post("/api/environment/environments").with(as(teacher)).contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.idEnvironment").value(id.toString()));
        return id;
    }
    private Map<String,Object> payload(UUID room, Instructor scheduled) {
        OffsetDateTime now = OffsetDateTime.now();
        return Map.of("idEnvironment",room,"idInstructorInCharge",scheduled.getIdInstructor(),"idChip",chip.getIdChip(),
                "deviceCode",unique(),"entryTime",now.toString(),"registrationMinutes",10,
                "exitTime",now.plusHours(1).toString(),"shutdownTime",now.plusHours(2).toString());
    }
    private JsonNode create(UUID room, Instructor scheduled, Instructor actor) throws Exception {
        return json.readTree(mvc.perform(post("/api/environment/sessions").with(as(actor)).contentType("application/json")
                .content(json.writeValueAsString(payload(room, scheduled)))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }
    private RequestPostProcessor as(Instructor instructor) { return as(instructor.getUser().getIdUser(),"INSTRUCTOR"); }
    private RequestPostProcessor as(UUID id, String role) {
        return authentication(new UsernamePasswordAuthenticationToken(id,null,List.of(new SimpleGrantedAuthority("ROLE_"+role))));
    }
    private Instructor instructor(InstructorType type) {
        Instructor value = new Instructor(); value.setUser(user()); value.setInstructorType(type); em.persist(value); return value;
    }
    private User user() {
        User value = new User(); value.setDocumentNumber(unique()); value.setFirstName("Session"); value.setLastName("Test");
        value.setAccountStatus(AccountStatus.ACTIVE); em.persist(value); return value;
    }
    private String unique() { return UUID.randomUUID().toString().replace("-","").substring(0,12); }
}
