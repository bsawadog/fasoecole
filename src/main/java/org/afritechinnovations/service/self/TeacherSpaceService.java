package org.afritechinnovations.service.self;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.academic.OwnerGradeDto;
import org.afritechinnovations.dto.self.SelfServiceDto;
import org.afritechinnovations.model.academic.ClassSubjectTeacher;
import org.afritechinnovations.model.academic.Evaluation;
import org.afritechinnovations.model.academic.Attendance;
import org.afritechinnovations.model.academic.AttendanceStatus;
import org.afritechinnovations.model.academic.SchoolClass;
import org.afritechinnovations.model.common.User;
import org.afritechinnovations.repository.common.UserRepository;
import org.afritechinnovations.model.communication.AbsenceReport;
import org.afritechinnovations.model.communication.AbsenceReportStatus;
import org.afritechinnovations.model.communication.FamilyAttendanceType;
import org.afritechinnovations.dto.communication.FamilyContactDto;
import org.afritechinnovations.repository.academic.AttendanceRepository;
import org.afritechinnovations.repository.communication.AbsenceReportRepository;
import org.afritechinnovations.model.people.EnrollmentStatus;
import org.afritechinnovations.model.people.StudentEnrollment;
import org.afritechinnovations.model.people.Teacher;
import org.afritechinnovations.model.people.TeacherScheduleSlot;
import org.afritechinnovations.repository.academic.ClassSubjectTeacherRepository;
import org.afritechinnovations.repository.academic.EvaluationRepository;
import org.afritechinnovations.repository.academic.SchoolClassRepository;
import org.afritechinnovations.repository.people.StudentEnrollmentRepository;
import org.afritechinnovations.repository.people.TeacherRepository;
import org.afritechinnovations.repository.people.TeacherScheduleSlotRepository;
import org.afritechinnovations.service.academic.OwnerGradeService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Espace enseignant : classes, élèves, emploi du temps et saisie des notes.
 * <p>
 * L'enseignant n'agit que sur les classes où il a une affectation active et sur les évaluations de ses propres
 * matières. Ces contrôles faits, le calcul et la traçabilité des notes sont délégués à {@link OwnerGradeService}
 * (en mode « système », l'auteur enregistré dans l'historique restant l'enseignant).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TeacherSpaceService {

    private static final List<EnrollmentStatus> ROSTER_STATUSES =
            List.of(EnrollmentStatus.ACTIVE, EnrollmentStatus.COMPLETED);

    private final TeacherRepository teacherRepository;
    private final ClassSubjectTeacherRepository classSubjectTeacherRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final StudentEnrollmentRepository studentEnrollmentRepository;
    private final TeacherScheduleSlotRepository scheduleSlotRepository;
    private final EvaluationRepository evaluationRepository;
    private final OwnerGradeService gradeService;
    private final AbsenceReportRepository absenceReportRepository;
    private final AttendanceRepository attendanceRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<org.afritechinnovations.dto.self.TeacherAttendanceDto.Item> attendance(Long userId, Long classId, LocalDate date) {
        requireTaughtClass(userId, classId);
        Map<Long, Attendance> recorded = attendanceRepository.findByClassBetween(classId,date,date).stream()
                .collect(Collectors.toMap(a -> a.getStudent().getId(), a -> a, (a,b) -> b));
        return roster(classId).stream().map(enrollment -> {
            var student = enrollment.getStudent(); var row = recorded.get(student.getId());
            return new org.afritechinnovations.dto.self.TeacherAttendanceDto.Item(student.getId(), student.getFullName(),
                    student.getRegistrationNumber(), row == null ? null : row.getStatus(), row == null ? null : row.getJustification(), row == null ? null : row.getVersion());
        }).toList();
    }

    public void saveAttendance(Long userId, Long classId, Long studentId, org.afritechinnovations.dto.self.TeacherAttendanceDto.Request request) {
        SchoolClass schoolClass = requireTaughtClass(userId, classId);
        validateAttendanceDate(schoolClass, request.date());
        var enrollment = roster(classId).stream().filter(e -> e.getStudent().getId().equals(studentId)).findFirst()
                .orElseThrow(() -> new AccessDeniedException("Cet élève n'est pas inscrit dans votre classe"));
        Attendance attendance = attendanceRepository.findByStudentIdAndSchoolClassIdAndAttendanceDate(studentId,classId,request.date())
                .orElseGet(() -> Attendance.builder().student(enrollment.getStudent()).schoolClass(schoolClass).attendanceDate(request.date()).build());
        attendance.setStatus(request.status());
        attendance.setJustification(request.justification() == null || request.justification().isBlank() ? null : request.justification().trim());
        attendanceRepository.save(attendance);
    }

    /** Validate the complete call before writing; a failed row rolls back the entire call. */
    public List<org.afritechinnovations.dto.self.TeacherAttendanceDto.Item> saveAttendanceBatch(Long userId,Long classId,org.afritechinnovations.dto.self.TeacherAttendanceDto.Batch request) {
        SchoolClass cls=requireTaughtClass(userId,classId);
        validateAttendanceDate(cls, request.date());
        if(request.entries()==null || request.entries().isEmpty() || request.entries().size()>2000)throw new IllegalArgumentException("Appel vide ou trop volumineux.");
        var students=roster(classId).stream().collect(Collectors.toMap(e->e.getStudent().getId(),e->e.getStudent()));
        var recorded=attendanceRepository.findByClassBetween(classId,request.date(),request.date()).stream().collect(Collectors.toMap(a->a.getStudent().getId(),a->a,(a,b)->b));
        var seen=new java.util.HashSet<Long>();
        for(var entry:request.entries()) {
            if(entry==null || entry.studentId()==null || !students.containsKey(entry.studentId()))throw new AccessDeniedException("Un élève n’est plus inscrit dans cette classe. Actualisez la liste.");
            if(!seen.add(entry.studentId()) || entry.status()==null || entry.justification()!=null && entry.justification().length()>500)throw new IllegalArgumentException("Vérifiez les statuts et motifs de l’appel.");
            var existing=recorded.get(entry.studentId());
            if(!java.util.Objects.equals(existing==null?null:existing.getStatus(),entry.expectedStatus()))
                throw new IllegalArgumentException("Une présence a été modifiée depuis le chargement. Actualisez l’appel avant de l’enregistrer.");
        }
        for(var entry:request.entries()) {
            var attendance=recorded.get(entry.studentId());
            if(attendance==null)attendance=Attendance.builder().student(students.get(entry.studentId())).schoolClass(cls).attendanceDate(request.date()).build();
            attendance.setStatus(entry.status());
            attendance.setJustification(entry.justification()==null || entry.justification().isBlank()?null:entry.justification().trim());
            attendanceRepository.save(attendance);
        }
        return attendance(userId,classId,request.date());
    }

    private void validateAttendanceDate(SchoolClass cls, LocalDate date) {
        if (date == null) throw new IllegalArgumentException("Choisissez la date de l’appel.");
        var format = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
        var year = cls.getAcademicYear();
        if (date.isBefore(year.getStartDate()) || date.isAfter(year.getEndDate()))
            throw new IllegalArgumentException("L’appel du " + date.format(format) + " est hors de l’année scolaire « "
                    + year.getLabel() + " » de la classe « " + cls.getName() + " » (du "
                    + year.getStartDate().format(format) + " au " + year.getEndDate().format(format)
                    + "). Choisissez une date dans cette période ou vérifiez la configuration de l’année scolaire.");
        var today = LocalDate.now();
        if (date.isAfter(today)) throw new IllegalArgumentException("L’appel du " + date.format(format)
                + " est dans le futur. La date actuelle du serveur est le " + today.format(format) + ".");
    }

    // ------------------------------------------------------------------ classes et élèves

    @Transactional(readOnly = true)
    public List<SelfServiceDto.TeacherClass> classes(Long userId) {
        Map<Long, List<ClassSubjectTeacher>> byClass = activeAssignments(userId).stream()
                .collect(Collectors.groupingBy(cst -> cst.getSchoolClass().getId(), LinkedHashMap::new,
                        Collectors.toList()));
        return byClass.values().stream()
                .map(rows -> {
                    SchoolClass cls = rows.get(0).getSchoolClass();
                    List<SelfServiceDto.TeacherSubject> subjects = rows.stream()
                            .map(cst -> new SelfServiceDto.TeacherSubject(cst.getId(), cst.getSubject().getId(),
                                    cst.getSubject().getName()))
                            .sorted(Comparator.comparing(SelfServiceDto.TeacherSubject::subjectName,
                                    String.CASE_INSENSITIVE_ORDER))
                            .toList();
                    return new SelfServiceDto.TeacherClass(cls.getId(), cls.getName(),
                            cls.getLevel() == null ? null : cls.getLevel().getName(), cls.getSchool().getId(),
                            cls.getSchool().getName(), cls.getAcademicYear().getId(),
                            cls.getAcademicYear().getLabel(), Boolean.TRUE.equals(cls.getAcademicYear().getIsCurrent()),
                            roster(cls.getId()).size(), subjects,
                            cls.getAcademicYear().getStartDate(), cls.getAcademicYear().getEndDate());
                })
                .sorted(Comparator.comparing(SelfServiceDto.TeacherClass::currentYear).reversed()
                        .thenComparing(SelfServiceDto.TeacherClass::schoolName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(SelfServiceDto.TeacherClass::className, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SelfServiceDto.RosterStudent> students(Long userId, Long classId) {
        requireTaughtClass(userId, classId);
        return roster(classId).stream()
                .map(se -> new SelfServiceDto.RosterStudent(se.getStudent().getId(), se.getStudent().getFullName(),
                        se.getStudent().getRegistrationNumber(), se.getStudent().getGender(),
                        se.getStudent().getBirthDate()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FamilyContactDto.AbsenceReportItem> familyReports(Long userId, Long classId, LocalDate date) {
        SchoolClass schoolClass = requireTaughtClass(userId, classId);
        Set<Long> studentIds = roster(classId).stream().map(se -> se.getStudent().getId())
                .collect(Collectors.toSet());
        return absenceReportRepository.findBySchool(schoolClass.getSchool().getId()).stream()
                .filter(report -> studentIds.contains(report.getStudent().getId()))
                .filter(report -> report.getStatus() == AbsenceReportStatus.PENDING)
                .filter(report -> !date.isBefore(report.getStartDate()) && !date.isAfter(report.getEndDate()))
                .map(report -> org.afritechinnovations.service.communication.FamilyContactMapper.report(report, 0))
                .toList();
    }

    public FamilyContactDto.AbsenceReportItem recordFamilyReport(Long userId, Long classId, Long reportId) {
        SchoolClass schoolClass = requireTaughtClass(userId, classId);
        AbsenceReport report = absenceReportRepository.findByIdForUpdate(reportId)
                .orElseThrow(() -> new IllegalArgumentException("Signalement introuvable : " + reportId));
        boolean enrolled = roster(classId).stream()
                .anyMatch(enrollment -> enrollment.getStudent().getId().equals(report.getStudent().getId()));
        if (!enrolled || !report.getSchool().getId().equals(schoolClass.getSchool().getId())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Cet élève ne relève pas de cette classe");
        }
        if (report.getStatus() != AbsenceReportStatus.PENDING) {
            throw new IllegalArgumentException("Ce signalement a déjà été traité");
        }
        if (!report.getStartDate().equals(report.getEndDate())) {
            throw new IllegalArgumentException("Ce signalement concerne plusieurs jours ; traitez-le depuis la boîte de l'établissement");
        }
        AttendanceStatus attendanceStatus = report.getAttendanceType() == FamilyAttendanceType.LATE
                ? AttendanceStatus.LATE : AttendanceStatus.ABSENT;
        Attendance attendance = attendanceRepository.findByStudentIdAndSchoolClassIdAndAttendanceDate(
                        report.getStudent().getId(), classId, report.getStartDate())
                .orElseGet(() -> Attendance.builder()
                        .student(report.getStudent())
                        .schoolClass(schoolClass)
                        .attendanceDate(report.getStartDate())
                        .build());
        attendance.setStatus(attendanceStatus);
        attendance.setJustification(report.getReason());
        attendanceRepository.save(attendance);

        report.setStatus(AbsenceReportStatus.ACKNOWLEDGED);
        report.setSchoolComment("Enregistré par l’enseignant.");
        report.setHandledBy(userRepository.getReferenceById(userId));
        report.setHandledAt(LocalDateTime.now());
        absenceReportRepository.save(report);
        return org.afritechinnovations.service.communication.FamilyContactMapper.report(report, 1);
    }

    public boolean teachesStudent(Long userId, Long studentId) {
        Set<Long> classIds = activeAssignments(userId).stream()
                .map(assignment -> assignment.getSchoolClass().getId()).collect(Collectors.toSet());
        return studentEnrollmentRepository.findByStudentId(studentId).stream()
                .anyMatch(enrollment -> enrollment.getStatus() == EnrollmentStatus.ACTIVE
                        && classIds.contains(enrollment.getSchoolClass().getId()));
    }

    @Transactional(readOnly = true)
    public List<SelfServiceDto.ScheduleEntry> schedule(Long userId) {
        LocalDate today = LocalDate.now();
        List<SelfServiceDto.ScheduleEntry> entries = new java.util.ArrayList<>();
        for (Teacher teacher : teacherRepository.findByUserId(userId)) {
            Map<Long, String> subjectsByClass = classSubjectTeacherRepository
                    .findAllWithSubjectAndClassByTeacherId(teacher.getId()).stream()
                    .filter(a -> a.isActive() || a.getSchoolClass().getAcademicYear().isClosed())
                .filter(a -> org.afritechinnovations.service.academic.SelectedAcademicYear.matches(a.getSchoolClass().getAcademicYear()))
                    .collect(Collectors.groupingBy(cst -> cst.getSchoolClass().getId(),
                            Collectors.mapping(cst -> cst.getSubject().getName(),
                                    Collectors.collectingAndThen(Collectors.toCollection(java.util.TreeSet::new),
                                            set -> String.join(", ", set)))));
            for (TeacherScheduleSlot slot : scheduleSlotRepository.findAllWithClassByTeacherId(teacher.getId())) {
                if (!org.afritechinnovations.service.academic.SelectedAcademicYear.matches(slot.getSchoolClass().getAcademicYear()) || !isEffective(slot, org.afritechinnovations.service.academic.SelectedAcademicYear.viewDate(slot.getSchoolClass().getSchool().getId(), today))) {
                    continue;
                }
                SchoolClass cls = slot.getSchoolClass();
                entries.add(new SelfServiceDto.ScheduleEntry(slot.getId(), slot.getDayOfWeek(), slot.getStartTime(),
                        slot.getEndTime(), cls.getId(), cls.getName(), cls.getSchool().getName(),
                        subjectsByClass.get(cls.getId()), fullName(teacher.getUser())));
            }
        }
        entries.sort(Comparator.comparingInt(SelfServiceDto.ScheduleEntry::dayOfWeek)
                .thenComparing(SelfServiceDto.ScheduleEntry::startTime));
        return entries;
    }

    // ------------------------------------------------------------------ notes

    @Transactional(readOnly = true)
    public List<OwnerGradeDto.PeriodInfo> periods(Long userId, Long classId) {
        SchoolClass cls = requireTaughtClass(userId, classId);
        return gradeService.listPeriods(cls.getSchool().getId(), userId, true).stream()
                .filter(p -> p.academicYearId().equals(cls.getAcademicYear().getId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OwnerGradeDto.EvaluationInfo> evaluations(Long userId, Long classId, Long periodId) {
        requireTaughtClass(userId, classId);
        Set<Long> mine = assignmentIds(userId);
        return gradeService.listEvaluations(classId, periodId, userId, true).stream()
                .filter(e -> mine.contains(e.classSubjectTeacherId()))
                .toList();
    }

    public OwnerGradeDto.EvaluationInfo createEvaluation(Long userId, Long classId,
                                                         OwnerGradeDto.EvaluationRequest request) {
        requireTaughtClass(userId, classId);
        requireOwnAssignment(userId, request.classSubjectTeacherId());
        return gradeService.createEvaluation(classId, request, userId, true);
    }

    public OwnerGradeDto.EvaluationInfo updateEvaluation(Long userId, Long evaluationId,
                                                         OwnerGradeDto.EvaluationRequest request) {
        requireOwnEvaluation(userId, evaluationId);
        requireOwnAssignment(userId, request.classSubjectTeacherId());
        return gradeService.updateEvaluation(evaluationId, request, userId, true);
    }

    public void deleteEvaluation(Long userId, Long evaluationId, String reason) {
        requireOwnEvaluation(userId, evaluationId);
        gradeService.deleteEvaluation(evaluationId, reason, userId, true);
    }

    @Transactional(readOnly = true)
    public OwnerGradeDto.GradeSheet gradeSheet(Long userId, Long evaluationId) {
        requireOwnEvaluation(userId, evaluationId);
        return gradeService.gradeSheet(evaluationId, userId, true);
    }

    public OwnerGradeDto.SaveGradesResult saveGrades(Long userId, Long evaluationId,
                                                     OwnerGradeDto.SaveGradesRequest request) {
        requireOwnEvaluation(userId, evaluationId);
        return gradeService.saveGrades(evaluationId, request, userId, true);
    }

    // ------------------------------------------------------------------ contrôles d'accès

    private List<ClassSubjectTeacher> activeAssignments(Long userId) {
        return teacherRepository.findByUserId(userId).stream()
                .flatMap(t -> classSubjectTeacherRepository.findAllWithSubjectAndClassByTeacherId(t.getId()).stream())
                .filter(a -> a.getSchoolClass().getSchool().getStatus() == org.afritechinnovations.model.common.SchoolStatus.ACTIVE)
                .filter(a -> a.isActive() || a.getSchoolClass().getAcademicYear().isClosed())
                .toList();
    }

    private Set<Long> assignmentIds(Long userId) {
        return activeAssignments(userId).stream().map(ClassSubjectTeacher::getId).collect(Collectors.toSet());
    }

    public SchoolClass requireTaughtClass(Long userId, Long classId) {
        boolean teaches = teacherRepository.findByUserId(userId).stream()
                .anyMatch(t -> classSubjectTeacherRepository.existsBySchoolClassIdAndTeacherIdAndActiveTrue(classId,
                        t.getId()));
        if (!teaches) {
            throw new AccessDeniedException("Vous n'enseignez pas dans cette classe");
        }
        return schoolClassRepository.findById(classId)
                .orElseThrow(() -> new IllegalArgumentException("Classe introuvable : " + classId));
    }

    private void requireOwnAssignment(Long userId, Long classSubjectTeacherId) {
        if (classSubjectTeacherId == null || !assignmentIds(userId).contains(classSubjectTeacherId)) {
            throw new AccessDeniedException("Vous ne pouvez évaluer que vos propres matières");
        }
    }

    private void requireOwnEvaluation(Long userId, Long evaluationId) {
        Evaluation evaluation = evaluationRepository.findById(evaluationId)
                .orElseThrow(() -> new IllegalArgumentException("Évaluation introuvable : " + evaluationId));
        ClassSubjectTeacher cst = evaluation.getClassSubjectTeacher();
        if (cst.getSchoolClass().getSchool().getStatus() != org.afritechinnovations.model.common.SchoolStatus.ACTIVE)
            throw new AccessDeniedException("Cet établissement est désactivé");
        if (!cst.isActive() || !cst.getTeacher().getUser().getId().equals(userId)) {
            throw new AccessDeniedException("Cette évaluation ne relève pas de vos matières");
        }
    }

    // ------------------------------------------------------------------ utilitaires

    private List<StudentEnrollment> roster(Long classId) {
        return studentEnrollmentRepository.findStudentsWithUserByClassIdAndStatusIn(classId, ROSTER_STATUSES).stream()
                .collect(Collectors.toMap(se -> se.getStudent().getId(), se -> se, (a, b) -> a, LinkedHashMap::new))
                .values().stream()
                .sorted(Comparator.comparing((StudentEnrollment se) -> se.getStudent().getLastName(),
                                String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(se -> se.getStudent().getFirstName(), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    static boolean isEffective(TeacherScheduleSlot slot, LocalDate today) {
        return !slot.getEffectiveFrom().isAfter(today)
                && (slot.getEffectiveTo() == null || !slot.getEffectiveTo().isBefore(today));
    }

    static String fullName(User user) {
        return (user.getFirstName() + " " + user.getLastName()).trim();
    }
}
