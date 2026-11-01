package org.afritechinnovations.service.self;

import org.afritechinnovations.dto.academic.OwnerGradeDto;
import org.afritechinnovations.model.academic.AcademicYear;
import org.afritechinnovations.model.academic.ClassSubjectTeacher;
import org.afritechinnovations.model.academic.Evaluation;
import org.afritechinnovations.model.academic.SchoolClass;
import org.afritechinnovations.model.academic.Subject;
import org.afritechinnovations.model.common.School;
import org.afritechinnovations.model.common.User;
import org.afritechinnovations.model.people.Teacher;
import org.afritechinnovations.repository.academic.ClassSubjectTeacherRepository;
import org.afritechinnovations.repository.academic.EvaluationRepository;
import org.afritechinnovations.repository.academic.SchoolClassRepository;
import org.afritechinnovations.repository.people.StudentEnrollmentRepository;
import org.afritechinnovations.repository.people.TeacherRepository;
import org.afritechinnovations.repository.people.TeacherScheduleSlotRepository;
import org.afritechinnovations.service.academic.OwnerGradeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TeacherSpaceServiceTest {
    @Mock TeacherRepository teachers;
    @Mock ClassSubjectTeacherRepository assignments;
    @Mock SchoolClassRepository classes;
    @Mock StudentEnrollmentRepository enrollments;
    @Mock TeacherScheduleSlotRepository slots;
    @Mock EvaluationRepository evaluations;
    @Mock OwnerGradeService gradeService;
    @InjectMocks TeacherSpaceService service;

    private final User me = User.builder().id(5L).firstName("Issa").lastName("Ouédraogo").build();
    private final User other = User.builder().id(6L).firstName("Ali").lastName("Sawadogo").build();
    private final School school = School.builder().id(1L).name("École A").build();
    private final AcademicYear year = AcademicYear.builder().id(3L).label("2026-2027").isCurrent(true).build();
    private final SchoolClass cls = SchoolClass.builder().id(10L).name("CM1").school(school).academicYear(year).build();
    private final Teacher myTeacher = Teacher.builder().id(50L).user(me).school(school).build();
    private final Teacher otherTeacher = Teacher.builder().id(60L).user(other).school(school).build();
    private ClassSubjectTeacher maths;
    private ClassSubjectTeacher french;

    @BeforeEach
    void setUp() {
        maths = ClassSubjectTeacher.builder().id(100L).schoolClass(cls).teacher(myTeacher)
                .subject(Subject.builder().id(1L).name("Maths").build()).active(true).build();
        french = ClassSubjectTeacher.builder().id(200L).schoolClass(cls).teacher(otherTeacher)
                .subject(Subject.builder().id(2L).name("Français").build()).active(true).build();
        when(teachers.findByUserId(5L)).thenReturn(List.of(myTeacher));
        when(assignments.findAllWithSubjectAndClassByTeacherId(50L)).thenReturn(List.of(maths));
        when(assignments.existsBySchoolClassIdAndTeacherIdAndActiveTrue(10L, 50L)).thenReturn(true);
        when(classes.findById(10L)).thenReturn(Optional.of(cls));
        when(enrollments.findStudentsWithUserByClassIdAndStatusIn(anyLong(), any())).thenReturn(List.of());
    }

    @Test
    void listsOnlyTheClassesAndSubjectsOfTheTeacher() {
        year.setStartDate(LocalDate.of(2026, 9, 1));
        year.setEndDate(LocalDate.of(2027, 7, 31));
        var result = service.classes(5L);
        assertEquals(1, result.size());
        assertEquals("CM1", result.get(0).className());
        assertEquals(year.getStartDate(), result.get(0).academicYearStartDate());
        assertEquals(year.getEndDate(), result.get(0).academicYearEndDate());
        assertEquals(List.of("Maths"), result.get(0).subjects().stream().map(s -> s.subjectName()).toList());
    }

    @Test
    void explainsTheClassAcademicYearWhenAnAttendanceDateIsOutsideItsBounds() {
        year.setStartDate(LocalDate.of(2020, 9, 1));
        year.setEndDate(LocalDate.of(2021, 7, 31));
        var error = assertThrows(IllegalArgumentException.class, () -> service.saveAttendanceBatch(5L, 10L,
                new org.afritechinnovations.dto.self.TeacherAttendanceDto.Batch(LocalDate.of(2021, 8, 1), List.of())));
        assertTrue(error.getMessage().contains("01/08/2021"));
        assertTrue(error.getMessage().contains("01/09/2020"));
        assertTrue(error.getMessage().contains("31/07/2021"));
        assertTrue(error.getMessage().contains("CM1"));
    }

    @Test
    void explainsTheServerDateWhenAnAttendanceDateIsInTheFuture() {
        year.setStartDate(LocalDate.now().minusDays(10));
        year.setEndDate(LocalDate.now().plusDays(10));
        var error = assertThrows(IllegalArgumentException.class, () -> service.saveAttendanceBatch(5L, 10L,
                new org.afritechinnovations.dto.self.TeacherAttendanceDto.Batch(LocalDate.now().plusDays(1), List.of())));
        assertTrue(error.getMessage().contains("dans le futur"));
        assertTrue(error.getMessage().contains("date actuelle du serveur"));
    }

    @Test
    void refusesAClassTheTeacherDoesNotTeach() {
        assertThrows(AccessDeniedException.class, () -> service.students(5L, 99L));
    }

    @Test
    void showsOnlyOwnEvaluations() {
        when(gradeService.listEvaluations(10L, 7L, 5L, true)).thenReturn(List.of(evaluationInfo(1L, 100L),
                evaluationInfo(2L, 200L)));
        var result = service.evaluations(5L, 10L, 7L);
        assertEquals(List.of(1L), result.stream().map(OwnerGradeDto.EvaluationInfo::id).toList());
    }

    @Test
    void cannotCreateAnEvaluationForAColleagueSubject() {
        var request = new OwnerGradeDto.EvaluationRequest(200L, 7L, "Dictée", "DEVOIR", LocalDate.now(),
                BigDecimal.valueOf(20), BigDecimal.ONE);
        assertThrows(AccessDeniedException.class, () -> service.createEvaluation(5L, 10L, request));
        verify(gradeService, never()).createEvaluation(anyLong(), any(), anyLong(), anyBoolean());
    }

    @Test
    void cannotGradeAColleagueEvaluation() {
        when(evaluations.findById(9L)).thenReturn(Optional.of(Evaluation.builder().id(9L)
                .classSubjectTeacher(french).build()));
        var request = new OwnerGradeDto.SaveGradesRequest(List.of(), null);
        assertThrows(AccessDeniedException.class, () -> service.saveGrades(5L, 9L, request));
        verify(gradeService, never()).saveGrades(anyLong(), any(), anyLong(), anyBoolean());
    }

    @Test
    void gradesOwnEvaluationThroughTheGradeEngine() {
        when(evaluations.findById(8L)).thenReturn(Optional.of(Evaluation.builder().id(8L)
                .classSubjectTeacher(maths).build()));
        var request = new OwnerGradeDto.SaveGradesRequest(List.of(), null);
        service.saveGrades(5L, 8L, request);
        verify(gradeService).saveGrades(8L, request, 5L, true);
    }

    private OwnerGradeDto.EvaluationInfo evaluationInfo(Long id, Long cstId) {
        return new OwnerGradeDto.EvaluationInfo(id, cstId, 1L, "x", "y", 7L, "t", "DEVOIR", LocalDate.now(),
                BigDecimal.valueOf(20), BigDecimal.ONE, 0, 0, null);
    }
}
