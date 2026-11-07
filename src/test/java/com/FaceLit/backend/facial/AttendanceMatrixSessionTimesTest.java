package com.FaceLit.backend.facial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowMapper;
import com.FaceLit.backend.academic.repository.InstructorRepository;
import com.FaceLit.backend.facial.dto.response.AttendanceMatrixResponseDTO;
import com.FaceLit.backend.facial.service.AttendanceMatrixService;
import com.FaceLit.backend.facial.service.FacialAccessPolicy;
import com.FaceLit.backend.facial.repository.AttendanceReadRepository;

class AttendanceMatrixSessionTimesTest {
    private final UUID actor = UUID.randomUUID();
    @org.junit.jupiter.api.BeforeEach void authenticate() {
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(actor, null,
                        List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_COORDINATOR"))));
    }
    @org.junit.jupiter.api.AfterEach void clearSecurity() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void exposesPersistedSessionTimesWithoutChangingAttendanceRules() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        InstructorRepository instructors = mock(InstructorRepository.class);
        when(instructors.findByUser_IdUser(any())).thenReturn(Optional.empty());
        ResultSet row = mock(ResultSet.class);
        UUID group = UUID.randomUUID();
        UUID environment = UUID.randomUUID();
        when(row.getObject(anyString(), eq(UUID.class))).thenAnswer(call -> {
            return "id_chip".equals(call.getArgument(0)) ? group : environment;
        });
        when(row.getObject("session_date", LocalDate.class)).thenReturn(LocalDate.of(2026, 10, 24));
        when(row.getString("session_start_time")).thenReturn("09:15");
        when(row.getString("session_end_time")).thenReturn("14:45");
        when(row.getString("actual_exit_time")).thenReturn("14:42");
        when(row.getString("entry_time")).thenReturn("09:18");
        when(row.getString("attendance_status")).thenReturn("PUNCTUAL");
        when(row.getBoolean("exit_registered")).thenReturn(true);
        when(jdbc.query(anyString(), any(PreparedStatementSetter.class), any(RowMapper.class)))
                .thenAnswer(call -> {
                    String sql = call.getArgument(0);
                    assertThat(sql).contains("re.entry_time", "re.exit_time", "fex.event_datetime", "actual_exit_time");
                    RowMapper mapper = call.getArgument(2);
                    return List.of(mapper.mapRow(row, 0));
                });
        AttendanceMatrixService service = new AttendanceMatrixService(
                new AttendanceReadRepository(jdbc), null, instructors, new FacialAccessPolicy());
        AttendanceMatrixResponseDTO result = service.getAttendanceMatrix(group,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), actor);
        var day = result.learners().get(0).days().get(0);
        assertThat(day.sessionStartTime()).isEqualTo("09:15");
        assertThat(day.sessionEndTime()).isEqualTo("14:45");
        assertThat(day.exitTime()).isEqualTo("14:42");
        assertThat(day.idEnvironment()).isEqualTo(environment);
        assertThat(day.entryTime()).isEqualTo("09:18");
        assertThat(day.status()).isEqualTo("punctual");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void returnsEmptyCollectionsWhenTheDatabaseReturnsNoSessions() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        InstructorRepository instructors = mock(InstructorRepository.class);
        when(instructors.findByUser_IdUser(any())).thenReturn(Optional.empty());
        when(jdbc.query(anyString(), any(PreparedStatementSetter.class), any(RowMapper.class))).thenReturn(List.of());
        AttendanceMatrixService service = new AttendanceMatrixService(
                new AttendanceReadRepository(jdbc), null, instructors, new FacialAccessPolicy());
        var result = service.getAttendanceMatrix(UUID.randomUUID(), LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31), actor);
        assertThat(result.learners()).isEmpty();
        assertThat(result.sessions()).isEmpty();
    }
}
