package org.afritechinnovations.controler.self;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.afritechinnovations.dto.self.ParentPortalDto.*;
import org.afritechinnovations.security.AccessGuard;
import org.afritechinnovations.service.communication.MessageDeliveryService;
import org.afritechinnovations.service.self.ParentPortalService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OfflinePublicationControllerTest {
    @Test void retriesMultipartPublicationWithFilesWithoutPublishingTwice() throws Exception {
        var mapper = new ObjectMapper().findAndRegisterModules();
        var jdbc = mock(JdbcTemplate.class);
        var guard = mock(AccessGuard.class);
        var service = mock(ParentPortalService.class);
        when(guard.currentUserId()).thenReturn(7L);
        var operation = UUID.randomUUID();
        var receipts = new ArrayList<Map<String,Object>>();
        when(jdbc.queryForList(startsWith("SELECT payload_hash"), eq(7L), eq(operation))).thenAnswer(call -> receipts);
        when(jdbc.update(startsWith("INSERT INTO offline_message_receipts"), eq(7L), eq(operation), anyString(), anyString()))
                .thenAnswer(call -> { receipts.add(Map.of("payload_hash", call.getArgument(3), "result_json", call.getArgument(4))); return 1; });
        var result = new Post(91L, Kind.HOMEWORK, 11L, "CP1", null, "Exercices", "Page 12", LocalDate.of(2099,11,30),
                LocalDateTime.of(2026,11,1,12,0), List.of(new FileItem(1L,"cours.txt",5)), true);
        when(service.teacherPublish(eq(11L), any(), any())).thenReturn(result);
        var mvc = MockMvcBuilders.standaloneSetup(new ParentPortalController(service, guard, new MessageDeliveryService(jdbc, mapper))).build();
        var payload = new PostRequest(Kind.HOMEWORK,11L,null,"Exercices","Page 12",LocalDate.of(2099,11,30));
        var request = new MockMultipartFile("request","request.json","application/json",mapper.writeValueAsBytes(payload));
        var file = new MockMultipartFile("files","cours.txt","text/plain","Cours".getBytes());
        var first = mvc.perform(multipart("/api/me/teacher/classes/11/portal/posts").file(request).file(file)
                .header("X-Offline-Publication-Id",operation)).andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(91)).andReturn();
        var retry = mvc.perform(multipart("/api/me/teacher/classes/11/portal/posts").file(request).file(file)
                .header("X-Offline-Publication-Id",operation)).andExpect(status().isCreated()).andReturn();
        assertEquals(mapper.readTree(first.getResponse().getContentAsString()), mapper.readTree(retry.getResponse().getContentAsString()));
        verify(service,times(1)).teacherPublish(eq(11L),eq(payload),any());
        assertEquals(1,receipts.size());
        var changed = new MockMultipartFile("files","cours.txt","text/plain","Autre cours".getBytes());
        var failure = assertThrows(jakarta.servlet.ServletException.class, () -> mvc.perform(multipart("/api/me/teacher/classes/11/portal/posts")
                .file(request).file(changed).header("X-Offline-Publication-Id",operation)));
        assertInstanceOf(IllegalArgumentException.class,failure.getCause());
        verify(service,times(1)).teacherPublish(eq(11L),any(),any());
    }
}
