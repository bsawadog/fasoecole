package org.afritechinnovations.service.self;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.afritechinnovations.model.academic.*;
import org.afritechinnovations.model.people.Student;
import org.afritechinnovations.repository.academic.AttendanceRepository;
import org.afritechinnovations.repository.academic.SchoolClassRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class OfflineAttendanceServiceTest {
    @Mock JdbcTemplate jdbc;
    @Mock TeacherSpaceService teacher;
    @Mock SchoolClassRepository classes;
    @Mock AttendanceRepository attendance;
    OfflineAttendanceService service;
    AcademicYear year = AcademicYear.builder().build();
    SchoolClass cls = SchoolClass.builder().id(11L).academicYear(year).build();
    final LocalDate date = LocalDate.now();
    final UUID operation = UUID.randomUUID();
    OfflineAttendanceService.Batch batch() {
        return new OfflineAttendanceService.Batch(operation, date, List.of(new OfflineAttendanceService.Entry(
                5L, AttendanceStatus.ABSENT, "Malade", null, null, null)));
    }
    @BeforeEach void setup() {
        service = new OfflineAttendanceService(jdbc, teacher, classes, attendance, new ObjectMapper().findAndRegisterModules());
    }
    void authorized() {
        when(teacher.requireTaughtClass(7L, 11L)).thenReturn(cls);
        when(classes.lockForRosterImport(11L)).thenReturn(Optional.of(cls));
    }
    @Test void recordsReceiptAndDoesNotReapplyAfterLostResponse() {
        authorized();
        service.synchronize(7L, 11L, batch());
        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(contains("INSERT INTO offline_attendance_receipts"), eq(7L), eq(operation), eq(11L), hash.capture(), anyString());
        when(jdbc.queryForList(anyString(), eq(String.class), eq(7L), eq(operation))).thenReturn(List.of(hash.getValue()));
        when(jdbc.queryForObject(contains("SELECT result_json"), eq(String.class), eq(7L), eq(operation))).thenReturn("[]");
        service.synchronize(7L, 11L, batch());
        verify(teacher, times(1)).saveAttendanceBatch(eq(7L), eq(11L), any());
        verify(attendance, times(1)).flush();
    }
    @Test void rejectsChangedJustificationEvenWhenStatusHasNotChanged() {
        authorized();
        var row = Attendance.builder().student(Student.builder().id(5L).build()).status(AttendanceStatus.ABSENT).justification("Corrigé").version(2L).build();
        when(attendance.findByClassBetween(11L, date, date)).thenReturn(List.of(row));
        var request = new OfflineAttendanceService.Batch(operation, date, List.of(new OfflineAttendanceService.Entry(
                5L, AttendanceStatus.PRESENT, null, AttendanceStatus.ABSENT, "Ancien motif", 2L)));
        assertThrows(IllegalArgumentException.class, () -> service.synchronize(7L, 11L, request));
        verify(teacher, never()).saveAttendanceBatch(anyLong(), anyLong(), any());
    }
    @Test void rejectsChangedVersionAndClosedYearsWithoutWriting() {
        authorized();
        var row = Attendance.builder().student(Student.builder().id(5L).build()).status(AttendanceStatus.ABSENT).version(3L).build();
        when(attendance.findByClassBetween(11L, date, date)).thenReturn(List.of(row));
        var request = new OfflineAttendanceService.Batch(operation, date, List.of(new OfflineAttendanceService.Entry(
                5L, AttendanceStatus.PRESENT, null, AttendanceStatus.ABSENT, null, 2L)));
        assertThrows(IllegalArgumentException.class, () -> service.synchronize(7L, 11L, request));
        year.setClosedAt(java.time.LocalDateTime.now());
        assertThrows(IllegalArgumentException.class, () -> service.synchronize(7L, 11L, request));
        verify(teacher, never()).saveAttendanceBatch(anyLong(), anyLong(), any());
    }
    @Test void checksCurrentRightsBeforeAcceptingAnOldReceipt() {
        when(teacher.requireTaughtClass(7L, 11L)).thenThrow(new AccessDeniedException("Classe non affectée"));
        assertThrows(AccessDeniedException.class, () -> service.synchronize(7L, 11L, batch()));
        verifyNoInteractions(jdbc, attendance, classes);
    }
}
