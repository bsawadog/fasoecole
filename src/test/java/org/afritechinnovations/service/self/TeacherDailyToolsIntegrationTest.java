package org.afritechinnovations.service.self;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.flywaydb.core.Flyway;
import org.afritechinnovations.model.common.*;
import org.afritechinnovations.model.academic.*;
import org.afritechinnovations.model.people.*;
import org.afritechinnovations.repository.people.TeacherRepository;
import org.afritechinnovations.dto.self.SelfServiceDto;
import org.afritechinnovations.service.communication.ConversationMessagingService;
import org.afritechinnovations.dto.communication.FamilyContactDto;
import org.springframework.security.access.AccessDeniedException;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.afritechinnovations.service.self.TeacherDailyToolsService.*;

@EnabledIfSystemProperty(named="dailyToolsTestUrl",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/daily_tools_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TeacherDailyToolsIntegrationTest {
 JdbcTemplate jdbc;TeacherSpaceService teacher;FamilySpaceService family;TeacherRepository teachers;ConversationMessagingService messages;TeacherDailyToolsService service;
 final LocalDate today=LocalDate.now();
 School school;SchoolClass cls;Teacher profile;
 @BeforeAll void migrate(){
  var source=new DriverManagerDataSource(System.getProperty("dailyToolsTestUrl"),"postgres","disposable-test-only");
  Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();jdbc=new JdbcTemplate(source);
  jdbc.update("INSERT INTO users(id,first_name,last_name,password_hash) VALUES(105,'Teacher','A','test'),(106,'Teacher','B','test'),(107,'Child','A','test'),(108,'Child','Other','test'),(109,'Owner','A','test'),(110,'Child','B','test')");
  jdbc.update("INSERT INTO schools(id,name,type,owner_id,status) VALUES(101,'A','PRIMAIRE',109,'ACTIVE'),(102,'Other','PRIMAIRE',109,'ACTIVE')");
  jdbc.update("INSERT INTO academic_years(id,school_id,label,start_date,end_date,is_current) VALUES(401,101,'Test',CURRENT_DATE-365,CURRENT_DATE+365,true),(402,102,'Test',CURRENT_DATE-365,CURRENT_DATE+365,true)");
  jdbc.update("INSERT INTO levels(id,school_id,name,cycle) VALUES(801,101,'CM1','PRIMAIRE'),(802,102,'CM1','PRIMAIRE')");
  jdbc.update("INSERT INTO classes(id,school_id,academic_year_id,level_id,name) VALUES(301,101,401,801,'CM1'),(302,102,402,802,'Other')");
  jdbc.update("INSERT INTO subjects(id,school_id,name) VALUES(501,101,'Maths'),(502,102,'Other')");
  jdbc.update("INSERT INTO teachers(id,user_id,school_id,employee_number) VALUES(201,105,101,'T1'),(202,106,101,'T2')");
  jdbc.update("INSERT INTO class_subject_teacher(id,class_id,subject_id,teacher_id,active) VALUES(601,301,501,201,true),(602,301,501,202,true)");
  jdbc.update("INSERT INTO students(id,user_id,school_id,registration_number,first_name,last_name) VALUES(701,107,101,'A','Child','A'),(702,108,102,'Other','Child','Other'),(703,110,101,'B','Child','B')");
  jdbc.update("INSERT INTO student_enrollments(student_id,class_id,academic_year_id,status) VALUES(701,301,401,'ACTIVE'),(702,302,402,'ACTIVE'),(703,301,401,'ACTIVE')");
 }
 @BeforeEach void reset(){
  jdbc.execute("TRUNCATE teacher_lesson_logs,teacher_private_notes,teacher_student_support,teacher_availability_requests,teacher_schedule_slots,homework_progress,parent_portal_posts,attendances,grades,evaluations RESTART IDENTITY CASCADE");
  teacher=mock(TeacherSpaceService.class);family=mock(FamilySpaceService.class);teachers=mock(TeacherRepository.class);messages=mock(ConversationMessagingService.class);
  service=new TeacherDailyToolsService(jdbc,teacher,family,teachers,messages);
  school=School.builder().id(101L).status(SchoolStatus.ACTIVE).owner(User.builder().id(109L).active(true).approved(true).build()).build();
  var year=AcademicYear.builder().id(401L).isCurrent(true).startDate(today.minusYears(1)).endDate(today.plusYears(1)).build();
  cls=SchoolClass.builder().id(301L).school(school).academicYear(year).build();profile=Teacher.builder().id(201L).school(school).user(User.builder().id(105L).build()).build();
  when(teacher.requireTaughtClass(105L,301L)).thenReturn(cls);
  when(teacher.requireTaughtClass(105L,302L)).thenThrow(new AccessDeniedException("Forbidden"));
  when(teachers.findByUserId(105L)).thenReturn(List.of(profile));when(teachers.findByUserId(106L)).thenReturn(List.of(profile));
  var detail=mock(SelfServiceDto.StudentOverview.class);when(detail.classId()).thenReturn(301L);when(detail.schoolId()).thenReturn(101L);when(family.student(107L,701L)).thenReturn(detail);
  when(family.requireReadableStudent(107L,701L)).thenReturn(Student.builder().id(701L).school(school).build());
 }
 @Test void notesArePrivateAndCannotBeReadUpdatedOrDeletedAcrossAccounts(){
  service.note(105L,null,new NoteRequest("Préparer","Contenu privé",today,false));var note=service.notes(105L).getFirst();
  assertTrue(service.notes(106L).isEmpty());assertThrows(AccessDeniedException.class,()->service.note(106L,note.id(),new NoteRequest("Autre","Vol",null,false)));
  assertThrows(AccessDeniedException.class,()->service.removeNote(106L,note.id()));
  service.note(105L,note.id(),new NoteRequest("Préparer","Contenu privé",today,true));assertTrue(service.notes(105L).getFirst().done());
 }
 @Test void lessonsShareOnlyOnExplicitSelectionAndRemainBoundToOwnClassSubjectAndOpenYear(){
  var request=new LessonRequest(501L,today,"Fractions","Cours réalisé","Additionner","Exercice libre",false);
  service.lesson(105L,301L,null,request);assertTrue(service.familyLearning(107L,701L).lessons().isEmpty());
  var row=service.lessons(105L,301L).getFirst();service.lesson(105L,301L,row.id(),new LessonRequest(501L,today,"Fractions","Cours réalisé","Additionner","Exercice libre",true));
  assertEquals("Fractions",service.familyLearning(107L,701L).lessons().getFirst().title());
  assertThrows(AccessDeniedException.class,()->service.lessons(105L,302L));
  assertThrows(AccessDeniedException.class,()->service.lesson(105L,301L,null,new LessonRequest(502L,today,"Other","Other","","",true)));
  cls.getAcademicYear().setClosedAt(LocalDateTime.now());assertThrows(IllegalArgumentException.class,()->service.removeLesson(105L,301L,row.id()));
 }
 @Test void supportSeparatesPrivateActionsFromFamilyMessagesAndRejectsOtherSchoolStudents(){
  service.support(105L,301L,701L,new SupportRequest("Observation confidentielle",today.plusDays(7),false,"Réviser les fractions",false));
  assertTrue(service.familyLearning(107L,701L).support().isEmpty());
  service.support(105L,301L,701L,new SupportRequest("Observation confidentielle",today.plusDays(7),false,"Réviser les fractions",true));
  var familyResult=service.familyLearning(107L,701L);assertEquals("Réviser les fractions",familyResult.support().getFirst().message());assertFalse(familyResult.toString().contains("confidentielle"));
  assertThrows(AccessDeniedException.class,()->service.support(105L,301L,702L,new SupportRequest("Other",null,false,"",false)));
  assertThrows(IllegalArgumentException.class,()->service.support(105L,301L,701L,new SupportRequest("Action",null,false," ",true)));
 }
 @Test void automaticSupportUsesLatestDailyAttendanceAndOnlyOwnOverdueHomework(){
  for(int i=1;i<=3;i++)jdbc.update("INSERT INTO attendances(student_id,class_id,attendance_date,status) VALUES(701,301,?,'ABSENT')",today.minusDays(i));
  jdbc.update("INSERT INTO parent_portal_posts(school_id,class_id,student_id,author_id,kind,title,content,due_date) VALUES(101,301,NULL,105,'HOMEWORK','Own','Exercises',CURRENT_DATE-1),(101,301,703,106,'HOMEWORK','Other teacher','Exercises',CURRENT_DATE-1)");
  var row=service.support(105L,301L).stream().filter(s->s.studentId()==701L).findFirst().orElseThrow();assertEquals(3,row.absences());assertEquals(1,row.overdue());assertNull(row.average());
  jdbc.update("UPDATE attendances SET status='EXCUSED' WHERE student_id=701 AND attendance_date=?",today.minusDays(1));
  assertEquals(2,service.support(105L,301L).stream().filter(s->s.studentId()==701L).findFirst().orElseThrow().absences());
  assertEquals(1,service.support(105L,301L).stream().filter(s->s.studentId()==703L).findFirst().orElseThrow().overdue());
 }
 @Test void todayIncludesEffectiveCoursesAndPrivateDueRemindersAndDoesNotInventMissingNotes(){
  when(teacher.classes(105L)).thenReturn(List.of(new SelfServiceDto.TeacherClass(301L,"CM1",null,101L,"A",401L,"Test",true,2,List.of(),null,null)));
  jdbc.update("INSERT INTO teacher_schedule_slots(teacher_id,class_id,day_of_week,start_time,end_time,effective_from) VALUES(201,301,?,'09:00','10:00',CURRENT_DATE-1),(201,301,?,'11:00','12:00',CURRENT_DATE+1)",today.getDayOfWeek().getValue(),today.getDayOfWeek().getValue());
  service.note(105L,null,new NoteRequest("Rappel dû","Texte",today.minusDays(1),false));service.note(105L,null,new NoteRequest("Plus tard","Texte",today.plusDays(1),false));
  var day=service.today(105L);assertEquals(1,day.lessons().size());assertEquals(1,day.reminders().size());assertEquals(0,day.tasks().getFirst().count());assertEquals(0,day.tasks().getLast().count());
 }
 @Test void availabilityRejectsOtherProfilesAndInvalidDatesBeforeSendingAnything(){
  when(teachers.findById(201L)).thenReturn(Optional.of(profile));var start=LocalDateTime.now();
  assertThrows(AccessDeniedException.class,()->service.availability(106L,new AvailabilityRequest(201L,start,start.plusHours(2),"Motif",true)));
  assertThrows(IllegalArgumentException.class,()->service.availability(105L,new AvailabilityRequest(201L,start,start,"Motif",true)));verifyNoInteractions(messages);
 }
 @Test void indicativeGradesNormalizeOnlyOwnEvaluationsAndMissingMarksAreNotZeros(){
  when(teacher.classes(105L)).thenReturn(List.of(new SelfServiceDto.TeacherClass(301L,"CM1",null,101L,"A",401L,"Test",true,2,List.of(),null,null)));
  jdbc.update("INSERT INTO grade_periods(id,school_id,academic_year_id,code,name,start_date,end_date) VALUES(901,101,401,'T1','Trimestre',CURRENT_DATE-365,CURRENT_DATE+365) ON CONFLICT DO NOTHING");
  jdbc.update("INSERT INTO evaluations(id,class_subject_teacher_id,period_id,title,type,eval_date) VALUES(911,601,901,'Own','TEST',CURRENT_DATE),(912,602,901,'Other','TEST',CURRENT_DATE),(913,601,901,'Future','TEST',CURRENT_DATE+1)");
  jdbc.update("INSERT INTO grades(student_id,class_subject_teacher_id,term,type,value,max_value,evaluation_id) VALUES(701,601,'T1','TEST',16,40,911),(701,602,'T1','TEST',20,20,912)");
  var support=service.support(105L,301L);assertEquals(1,support.size());assertEquals(0,new java.math.BigDecimal("8").compareTo(support.getFirst().average()));
  assertEquals(1,service.today(105L).tasks().getLast().count());
  jdbc.update("INSERT INTO grades(student_id,class_subject_teacher_id,term,type,value,max_value,evaluation_id) VALUES(703,601,'T1','TEST',0,20,911)");
  assertEquals(0,service.today(105L).tasks().getLast().count());
  assertEquals(0,service.support(105L,301L).stream().filter(s->s.studentId()==703L).findFirst().orElseThrow().average().signum());
 }
 @Test void availabilityUsesSchoolOwnerAndPersistsTheActualConversationWithoutChangingPlanning(){
  when(teachers.findById(201L)).thenReturn(Optional.of(profile));
  Long conversation=jdbc.queryForObject("INSERT INTO school_conversations(school_id,parent_user_id,subject) VALUES(101,105,'Test') RETURNING id",Long.class);
  var summary=mock(FamilyContactDto.ConversationSummary.class);when(summary.id()).thenReturn(conversation);when(messages.sendTeacherMessage(eq(105L),any(),anyList())).thenReturn(List.of(summary));
  var start=LocalDateTime.now();service.availability(105L,new AvailabilityRequest(201L,start,start.plusHours(2),"Rendez-vous",true));
  var result=service.availability(105L).getFirst();assertEquals(conversation,result.conversationId());assertTrue(result.replacementRequested());
  var capture=org.mockito.ArgumentCaptor.forClass(FamilyContactDto.TeacherMessageRequest.class);verify(messages).sendTeacherMessage(eq(105L),capture.capture(),eq(List.of()));assertEquals(List.of(109L),capture.getValue().recipientUserIds());
  assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM teacher_schedule_slots",Integer.class));
 }
}
