package org.afritechinnovations.controler.self;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.afritechinnovations.security.AccessGuard;
import org.afritechinnovations.service.communication.ActionDeliveryService;
import org.afritechinnovations.service.communication.MessageDeliveryService;
import org.afritechinnovations.service.self.SchoolLifeService;
import org.afritechinnovations.service.self.TeacherDailyToolsService;
import org.afritechinnovations.service.self.StudentDashboardService;
import org.afritechinnovations.service.self.StudentStudyService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OfflineActionControllerTest {
    static Stream<Object[]> actions() {
        String observation = "{\"classId\":11,\"studentId\":4,\"kind\":\"INCIDENT\",\"observedOn\":\"2020-01-01\",\"description\":\"Observation\",\"action\":\"Discussion\",\"sharedWithFamily\":false}";
        return Stream.of(
            new Object[]{"/api/school-life/schools/1/events", "{\"title\":\"Réunion\",\"description\":\"Rencontre\",\"kind\":\"EVENT\",\"startsOn\":\"2099-01-01\",\"endsOn\":\"2099-01-01\"}"},
            new Object[]{"/api/school-life/schools/1/observations", observation},
            new Object[]{"/api/school-life/classes/11/observations", observation},
            new Object[]{"/api/school-life/students/4/requests", "{\"kind\":\"SCHOOL_CERTIFICATE\",\"reason\":\"Inscription\"}"},
            new Object[]{"/api/school-life/schools/1/books", "{\"title\":\"Calcul\",\"author\":\"Auteur\",\"reference\":\"REF-1\",\"copies\":2}"},
            new Object[]{"/api/me/teacher/classes/11/lessons", "{\"subjectId\":3,\"lessonOn\":\"2020-01-01\",\"title\":\"Calcul\",\"content\":\"Addition\",\"objectives\":\"Compter\",\"nextWork\":\"Exercices\",\"shared\":true}"},
            new Object[]{"/api/me/teacher/daily/notes", "{\"title\":\"Rappel\",\"content\":\"Préparer la séance\",\"done\":false}"},
            new Object[]{"/api/me/student/profiles/4/tasks", "{\"title\":\"Réviser\",\"details\":\"Calcul\",\"done\":false}"}
        );
    }
    @ParameterizedTest @MethodSource("actions")
    void createsEachSupportedActionOnlyOnceAcrossLostResponseRetries(String path, String payload) throws Exception {
        var mapper = new ObjectMapper().findAndRegisterModules();
        var jdbc = mock(JdbcTemplate.class);
        var guard = mock(AccessGuard.class); when(guard.currentUserId()).thenReturn(7L);
        var school = mock(SchoolLifeService.class); var daily = mock(TeacherDailyToolsService.class);
        var study = mock(StudentStudyService.class);
        var operation = UUID.randomUUID();
        var receipts = new ArrayList<Map<String,Object>>();
        when(jdbc.queryForList(startsWith("SELECT payload_hash"), eq(7L), eq(operation))).thenAnswer(call -> receipts);
        when(jdbc.update(startsWith("INSERT INTO offline_message_receipts"), eq(7L), eq(operation), anyString(), anyString()))
                .thenAnswer(call -> { receipts.add(Map.of("payload_hash",call.getArgument(3),"result_json",call.getArgument(4))); return 1; });
        var delivery = new ActionDeliveryService(new MessageDeliveryService(jdbc,mapper),guard);
        var mvc = MockMvcBuilders.standaloneSetup(new SchoolLifeController(school,delivery),new TeacherDailyToolsController(daily,guard,delivery),
                new StudentDashboardController(guard,mock(StudentDashboardService.class),study,delivery)).build();
        for (int attempt = 0; attempt < 2; attempt++) mvc.perform(post(path).contentType("application/json").content(payload)
                .header("X-Offline-Action-Id",operation)).andExpect(status().is2xxSuccessful());
        assertEquals(1,receipts.size());
        assertEquals(1,mockingDetails(school).getInvocations().size()+mockingDetails(daily).getInvocations().size()+mockingDetails(study).getInvocations().size());
        // The same operation cannot be replayed with a changed target or action type.
        String other = path.equals("/api/me/teacher/daily/notes") ? "/api/me/teacher/classes/11/lessons" : "/api/me/teacher/daily/notes";
        String otherPayload = other.endsWith("/notes") ? "{\"title\":\"Autre\",\"content\":\"Texte\",\"done\":false}"
                : "{\"subjectId\":3,\"lessonOn\":\"2020-01-01\",\"title\":\"Autre\",\"content\":\"Texte\",\"objectives\":\"\",\"nextWork\":\"\",\"shared\":false}";
        var failure = assertThrows(jakarta.servlet.ServletException.class,()->mvc.perform(post(other).contentType("application/json").content(otherPayload).header("X-Offline-Action-Id",operation)));
        assertInstanceOf(IllegalArgumentException.class,failure.getCause());
        assertEquals(1,mockingDetails(school).getInvocations().size()+mockingDetails(daily).getInvocations().size()+mockingDetails(study).getInvocations().size());
    }
}
