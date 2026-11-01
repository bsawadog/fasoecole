package org.afritechinnovations.service.communication;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import java.util.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class MessageDeliveryServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final MessageDeliveryService service = new MessageDeliveryService(jdbc, new ObjectMapper());
    private final UUID operation = UUID.randomUUID();
    private final TypeReference<List<Long>> type = new TypeReference<>() {};

    @Test void confirmsOnlyOnceAndReusesTheReceiptAfterALostResponse() {
        var receipts = new ArrayList<Map<String,Object>>();
        when(jdbc.queryForList(startsWith("SELECT payload_hash"), eq(7L), eq(operation))).thenAnswer(call -> receipts);
        when(jdbc.update(startsWith("INSERT INTO offline_message_receipts"), eq(7L), eq(operation), anyString(), anyString()))
                .thenAnswer(call -> { receipts.add(Map.of("payload_hash", call.getArgument(3), "result_json", call.getArgument(4))); return 1; });
        Supplier<List<Long>> send = mock(Supplier.class); when(send.get()).thenReturn(List.of(91L,92L));
        assertEquals(List.of(91L,92L), service.deliver(7L, operation, "teacher", Map.of("content","Bonjour"), List.of(), type, send));
        assertEquals(List.of(91L,92L), service.deliver(7L, operation, "teacher", Map.of("content","Bonjour"), List.of(), type, send));
        verify(send, times(1)).get();
        assertThrows(IllegalArgumentException.class, () -> service.deliver(7L, operation, "teacher", Map.of("content","Autre"), List.of(), type, send));
        var attachment = new MockMultipartFile("files", "cours.txt", "text/plain", new byte[]{1});
        assertThrows(IllegalArgumentException.class, () -> service.deliver(7L, operation, "teacher", Map.of("content","Bonjour"), List.of(attachment), type, send));
    }
    @Test void doesNotKeepAReceiptWhenDeliveryFails() {
        when(jdbc.queryForList(startsWith("SELECT payload_hash"), eq(7L), eq(operation))).thenReturn(List.of());
        assertThrows(IllegalArgumentException.class, () -> service.deliver(7L, operation, "reply/1", Map.of("content","Bonjour"), List.of(), type,
                () -> { throw new IllegalArgumentException("Destinataire non autorisé"); }));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }
    @Test void acceptsExistingClientsWithoutAnOperationId() {
        assertEquals(List.of(91L), service.deliver(7L, null, "start", Map.of(), List.of(), type, () -> List.of(91L)));
        verifyNoInteractions(jdbc);
    }
}
