package org.afritechinnovations.service.communication;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import org.afritechinnovations.security.AccessGuard;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.UUID;

/** Reuse transactional receipts for independent JSON creations returning no response body. */
@Service
@RequiredArgsConstructor
public class ActionDeliveryService {
    private final MessageDeliveryService delivery;
    private final AccessGuard guard;

    public void deliver(UUID operation, String route, Object payload, Runnable action) {
        delivery.deliver(guard.currentUserId(), operation, "action/" + route, payload, List.of(),
                new TypeReference<Boolean>() {}, () -> { action.run(); return true; });
    }
}
