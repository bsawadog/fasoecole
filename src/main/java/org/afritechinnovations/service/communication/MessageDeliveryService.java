package org.afritechinnovations.service.communication;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Supplier;

/** The receipt and message commit together, including a lost-response retry from another tab. */
@Service
@RequiredArgsConstructor
public class MessageDeliveryService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    @Transactional
    public <T> T deliver(Long user, UUID operation, String route, Object payload, List<MultipartFile> files,
                         TypeReference<T> type, Supplier<T> send) {
        if (operation == null) return send.get(); // Existing clients remain compatible.
        String hash = fingerprint(route, payload, files);
        // PostgreSQL releases this lock with the transaction; no race between two deliveries.
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", user + "/message/" + operation);
        var receipts = jdbc.queryForList("SELECT payload_hash,result_json FROM offline_message_receipts WHERE user_id=? AND operation_id=?", user, operation);
        if (!receipts.isEmpty()) {
            var receipt = receipts.getFirst();
            if (!hash.equals(receipt.get("payload_hash"))) throw new IllegalArgumentException("Cet identifiant d’envoi correspond à un autre message.");
            try { return mapper.readValue((String) receipt.get("result_json"), type); }
            catch (Exception ex) { throw new IllegalStateException("Impossible de retrouver la confirmation du message", ex); }
        }
        T result = send.get();
        try {
            jdbc.update("INSERT INTO offline_message_receipts(user_id,operation_id,payload_hash,result_json) VALUES(?,?,?,?)",
                    user, operation, hash, mapper.writeValueAsString(result));
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("Impossible de conserver la confirmation du message", ex);
        }
        return result;
    }

    private String fingerprint(String route, Object payload, List<MultipartFile> files) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            digest.update(mapper.writeValueAsBytes(List.of(route, payload)));
            if (files != null) for (var file : files) {
                digest.update(mapper.writeValueAsBytes(List.of(Objects.toString(file.getOriginalFilename(), ""), Objects.toString(file.getContentType(), ""), file.getSize())));
                digest.update(file.getBytes());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception ex) { throw new IllegalStateException("Impossible de vérifier le message", ex); }
    }
}
