package org.afritechinnovations.dto.self;

import org.afritechinnovations.dto.academic.OwnerGradeDto;
import org.afritechinnovations.model.academic.AttendanceStatus;
import org.afritechinnovations.model.academic.GradePeriodStatus;
import org.afritechinnovations.model.finance.InvoiceStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Contrats des espaces personnels (enseignant, élève, parent). */
public final class SelfServiceDto {

    private SelfServiceDto() {
    }

    // ------------------------------------------------------------------ enseignant

    public record TeacherSubject(Long classSubjectTeacherId, Long subjectId, String subjectName) {
    }

    public record TeacherClass(Long classId, String className, String levelName, Long schoolId, String schoolName,
                               Long academicYearId, String academicYearLabel, boolean currentYear,
                               int studentCount, List<TeacherSubject> subjects,
                               LocalDate academicYearStartDate, LocalDate academicYearEndDate) {
    }

    public record RosterStudent(Long studentId, String fullName, String registrationNumber, String gender,
                                LocalDate birthDate) {
    }

    public record ScheduleEntry(Long id, int dayOfWeek, LocalTime startTime, LocalTime endTime, Long classId,
                                String className, String schoolName, String subjects, String teacherName) {
    }

    // ------------------------------------------------------------------ élève / parent

    public record FeeSummary(BigDecimal totalDue, BigDecimal totalPaid, BigDecimal balance, int overdueCount) {
    }

    public record AttendanceSummary(long absences, long unjustifiedAbsences, long lates) {
    }

    public record StudentOverview(Long studentId, String fullName, String registrationNumber, LocalDate birthDate,
                                  String gender, String relationship, Long schoolId, String schoolName,
                                  Long classId, String className, String levelName, String academicYearLabel,
                                  AttendanceSummary attendance, FeeSummary fees) {
    }

    public record GradeItem(Long id, String subjectName, String title, String type, LocalDate date,
                            BigDecimal value, BigDecimal maxValue, String appreciation) {
        public GradeItem(Long id, String subjectName, String title, String type, LocalDate date,
                         BigDecimal value, BigDecimal maxValue) {
            this(id, subjectName, title, type, date, value, maxValue, null);
        }
    }

    public record PeriodGrades(Long periodId, String periodName, GradePeriodStatus status, LocalDate startDate,
                               LocalDate endDate, boolean published, List<GradeItem> grades,
                               OwnerGradeDto.Bulletin bulletin, BigDecimal classAverage) {
    }

    public record StudentGrades(Long studentId, String fullName, String className, String academicYearLabel,
                                List<PeriodGrades> periods) {
    }

    public record AttendanceItem(Long id, LocalDate date, AttendanceStatus status, String justification) {
    }

    public record InvoiceItem(Long id, String feeName, LocalDate dueDate, BigDecimal amountDue,
                              BigDecimal discountAmount, BigDecimal paid, BigDecimal balance, InvoiceStatus status) {
    }

    // ------------------------------------------------------------------ fiche de l'élève

    public record GuardianContact(String fullName, String relationship, String phone, String email) {
    }

    public record ClassTeacher(String fullName, String subjects) {
    }

    public record SchoolContact(Long schoolId, String name, String address, String phone, String email) {
    }

    public record StudentProfile(StudentOverview overview, String email, String phone,
                                 List<GuardianContact> guardians, List<ClassTeacher> teachers, SchoolContact school) {
    }
}
