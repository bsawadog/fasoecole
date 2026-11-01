package org.afritechinnovations.service.self;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.security.access.AccessDeniedException;
import org.afritechinnovations.dto.self.SelfServiceDto;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@EnabledIfSystemProperty(named="dashboardChartTestUrl",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/dashboard_chart_test")
class TeacherDashboardPostgresTest {
 SingleConnectionDataSource source;JdbcTemplate jdbc;TeacherSpaceService teacher;TeacherDashboardService service;
 @BeforeEach void setup(){
  source=new SingleConnectionDataSource(System.getProperty("dashboardChartTestUrl"),"chart_test","chart_test_only",true);jdbc=new JdbcTemplate(source);teacher=mock(TeacherSpaceService.class);service=new TeacherDashboardService(jdbc,teacher);
  when(teacher.classes(5L)).thenReturn(List.of(new SelfServiceDto.TeacherClass(11L,"CP1",null,1L,"Ecole",1L,"2026-2027",true,3,List.of(),null,null)));
  jdbc.execute("CREATE TEMP TABLE academic_years(id BIGINT,start_date DATE,end_date DATE,closed_at TIMESTAMP)");
  jdbc.execute("CREATE TEMP TABLE classes(id BIGINT,school_id BIGINT,academic_year_id BIGINT)");
  jdbc.execute("CREATE TEMP TABLE students(id BIGINT,school_id BIGINT)");
  jdbc.execute("CREATE TEMP TABLE attendances(id BIGINT,class_id BIGINT,student_id BIGINT,attendance_date DATE,status TEXT)");
  jdbc.execute("CREATE TEMP TABLE teachers(id BIGINT,user_id BIGINT,school_id BIGINT)");
  jdbc.execute("CREATE TEMP TABLE subjects(id BIGINT,name TEXT)");
  jdbc.execute("CREATE TEMP TABLE class_subject_teacher(id BIGINT,teacher_id BIGINT,subject_id BIGINT,class_id BIGINT,active BOOLEAN)");
  jdbc.execute("CREATE TEMP TABLE evaluations(id BIGINT,class_subject_teacher_id BIGINT,title TEXT,eval_date DATE)");
  jdbc.execute("CREATE TEMP TABLE grades(id BIGINT,student_id BIGINT,evaluation_id BIGINT,class_subject_teacher_id BIGINT,value NUMERIC,max_value NUMERIC)");
  jdbc.execute("CREATE TEMP TABLE parent_portal_posts(id BIGINT,class_id BIGINT,school_id BIGINT,student_id BIGINT,author_id BIGINT,kind TEXT,due_date DATE)");
  jdbc.execute("CREATE TEMP TABLE student_enrollments(class_id BIGINT,student_id BIGINT,academic_year_id BIGINT,status TEXT)");
  jdbc.execute("CREATE TEMP TABLE homework_progress(post_id BIGINT,student_id BIGINT,school_id BIGINT,status TEXT)");
  jdbc.update("INSERT INTO academic_years VALUES(1,'2026-09-01','2027-06-30',null),(2,'2026-09-01','2027-06-30',null)");
  jdbc.update("INSERT INTO classes VALUES(11,1,1),(12,2,2)");
  jdbc.update("INSERT INTO students VALUES(1,1),(2,1),(3,1),(4,2),(5,1),(6,1),(7,1)");
  jdbc.update("INSERT INTO attendances VALUES(1,11,1,'2026-10-12','ABSENT'),(2,11,1,'2026-10-12','LATE'),(3,11,1,'2026-10-13','PRESENT'),(4,11,2,'2026-10-12','ABSENT'),(5,11,3,'2026-10-13','EXCUSED'),(6,12,4,'2026-10-12','ABSENT'),(7,11,4,'2026-10-12','ABSENT'),(8,11,1,'2026-10-19','PRESENT')");
  jdbc.update("INSERT INTO teachers VALUES(50,5,1),(60,6,1),(70,5,2)");
  jdbc.update("INSERT INTO subjects VALUES(1,'Maths'),(2,'Francais')");
  jdbc.update("INSERT INTO class_subject_teacher VALUES(10,50,1,11,true),(20,60,2,11,true),(30,70,1,12,true)");
  jdbc.update("INSERT INTO evaluations VALUES(1,10,'Controle','2026-10-17'),(2,10,'Vide','2026-10-10'),(3,20,'Autre enseignant','2026-10-18'),(4,30,'Autre classe','2026-10-18')");
  jdbc.update("INSERT INTO grades VALUES(1,1,1,10,40,40),(2,2,1,10,0,20),(3,3,1,10,18,20),(4,4,1,10,40,40),(5,1,1,10,20,40),(6,5,1,10,0,0),(7,6,1,10,30,20),(8,7,1,20,20,20)");
  jdbc.update("INSERT INTO student_enrollments VALUES(11,1,1,'ACTIVE'),(11,1,1,'ACTIVE'),(11,2,1,'ACTIVE'),(11,3,1,'ACTIVE'),(11,7,1,'COMPLETED'),(12,4,2,'ACTIVE')");
  jdbc.update("INSERT INTO parent_portal_posts VALUES(1,11,1,null,5,'HOMEWORK','2026-10-17'),(2,11,1,null,6,'HOMEWORK','2026-10-17'),(3,11,1,2,5,'HOMEWORK','2026-10-20'),(4,12,2,null,5,'HOMEWORK','2026-10-17')");
  jdbc.update("INSERT INTO homework_progress VALUES(1,1,1,'SUBMITTED'),(1,2,1,'CORRECTED')");
 }
 @AfterEach void close(){source.destroy();}
 TeacherDashboardService.Dashboard dashboard(Long eval){return service.overview(5L,11L,eval,LocalDate.of(2026,10,18));}
 @Test void deduplicatesDailyStatusesWithoutCreatingDefaultPresenceRecords(){var d=dashboard(null);assertEquals(7,d.weeks().size());var w=d.weeks().getLast();assertEquals(1,w.present());assertEquals(1,w.absent());assertEquals(1,w.late());assertEquals(1,w.excused());assertEquals(0,d.weeks().getFirst().present());assertEquals(LocalDate.of(2026,9,1),d.weeks().getFirst().start());}
 @Test void normalizesGradesAndCountsRealZerosButNotMissingOrInvalidGrades(){var marks=dashboard(null).marks();assertEquals(1L,marks.evaluationId());assertEquals(2,marks.evaluations().size());assertEquals(3,marks.count());assertEquals(0,new BigDecimal("9.33").compareTo(marks.average()));assertEquals(List.of(1L,0L,1L,0L,1L),marks.buckets().stream().map(TeacherDashboardService.Bucket::count).toList());var empty=dashboard(2L).marks();assertEquals(0,empty.count());assertNull(empty.average());}
 @Test void countsOnlyOwnHomeworkRecipientsAndDeduplicatesEnrollments(){var h=dashboard(null).homework();assertEquals(2,h.todo());assertEquals(1,h.submitted());assertEquals(1,h.corrected());assertEquals(1,h.overdue());}
 @Test void rejectsAnotherClassOrTeachersEvaluation(){assertThrows(AccessDeniedException.class,()->service.overview(5L,12L,null));assertThrows(AccessDeniedException.class,()->dashboard(3L));assertThrows(AccessDeniedException.class,()->dashboard(4L));}
 @Test void supportsNoClassesAndFutureYearsWithoutFakeAttendance(){when(teacher.classes(5L)).thenReturn(List.of());assertNull(service.overview(5L,null,null).classId());when(teacher.classes(5L)).thenReturn(List.of(new SelfServiceDto.TeacherClass(11L,"CP1",null,1L,"Ecole",1L,"2026-2027",true,3,List.of(),null,null)));jdbc.update("UPDATE academic_years SET start_date='2027-01-01' WHERE id=1");assertTrue(dashboard(null).weeks().isEmpty());}
}
