package org.afritechinnovations.service.self;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.self.TeacherAttendanceDto;
import org.afritechinnovations.model.academic.AttendanceStatus;
import org.afritechinnovations.repository.academic.AttendanceRepository;
import org.afritechinnovations.repository.academic.SchoolClassRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class OfflineAttendanceService {
    private final JdbcTemplate jdbc;
    private final TeacherSpaceService teacher;
    private final SchoolClassRepository classes;
    private final AttendanceRepository attendance;
    private final ObjectMapper mapper;

    public record Entry(@NotNull Long studentId, @NotNull AttendanceStatus status,
                        @Size(max=500) String justification, AttendanceStatus expectedStatus,
                        @Size(max=500) String expectedJustification, @PositiveOrZero Long expectedVersion) {}
    public record Batch(@NotNull UUID operationId, @NotNull @PastOrPresent LocalDate date,
                        @NotEmpty @Size(max=2000) List<@Valid Entry> entries) {}

    @Transactional
    public String deviceKey(Long userId, UUID deviceId) {
        byte[] key = new byte[32]; new SecureRandom().nextBytes(key);
        jdbc.update("INSERT INTO offline_device_keys(user_id,device_id,encryption_key) VALUES(?,?,?) ON CONFLICT DO NOTHING",
                userId, deviceId, Base64.getEncoder().encodeToString(key));
        return jdbc.queryForObject("SELECT encryption_key FROM offline_device_keys WHERE user_id=? AND device_id=?",
                String.class, userId, deviceId);
    }

    /** Receipt and attendance writes commit together: retries never apply the call twice. */
    @Transactional
    public List<TeacherAttendanceDto.Item> synchronize(Long userId, Long classId, Batch request) {
        var cls = teacher.requireTaughtClass(userId, classId);
        classes.lockForRosterImport(classId).orElseThrow(() -> new IllegalArgumentException("Classe introuvable"));
        String hash = hash(classId, request);
        var receipts = jdbc.queryForList("SELECT payload_hash FROM offline_attendance_receipts WHERE user_id=? AND operation_id=?",
                String.class, userId, request.operationId());
        if (!receipts.isEmpty()) {
            if (!receipts.getFirst().equals(hash)) throw new IllegalArgumentException("Cet envoi a déjà été utilisé pour un autre appel.");
            String result = jdbc.queryForObject("SELECT result_json FROM offline_attendance_receipts WHERE user_id=? AND operation_id=?",
                    String.class, userId, request.operationId());
            if (result == null) throw new IllegalArgumentException("Cet ancien envoi a déjà été enregistré. Vérifiez l’appel actuel avant d’envoyer les corrections suivantes.");
            try { return mapper.readValue(result, new com.fasterxml.jackson.core.type.TypeReference<List<TeacherAttendanceDto.Item>>() {}); }
            catch (Exception ex) { throw new IllegalStateException("Impossible de retrouver la confirmation de cet envoi", ex); }
        }
        if (cls.getAcademicYear().isClosed()) throw new IllegalArgumentException("L’année scolaire est clôturée. Vos saisies locales sont conservées pour vérification.");
        var current = attendance.findByClassBetween(classId, request.date(), request.date()).stream()
                .collect(java.util.stream.Collectors.toMap(row -> row.getStudent().getId(), row -> row));
        for (var entry : request.entries()) {
            var row = current.get(entry.studentId());
            if (!Objects.equals(row == null ? null : row.getVersion(), entry.expectedVersion())
                    || !Objects.equals(row == null ? null : row.getStatus(), entry.expectedStatus())
                    || !Objects.equals(normalize(row == null ? null : row.getJustification()), normalize(entry.expectedJustification()))) {
                throw new IllegalArgumentException("Une présence ou son motif a changé depuis votre saisie. Vos modifications sont conservées : comparez-les avec l’appel actuel avant de réessayer.");
            }
        }
        teacher.saveAttendanceBatch(userId, classId, new TeacherAttendanceDto.Batch(request.date(), request.entries().stream()
                .map(entry -> new TeacherAttendanceDto.Entry(entry.studentId(), entry.status(), entry.justification(), entry.expectedStatus())).toList()));
        attendance.flush();
        var result = teacher.attendance(userId, classId, request.date());
        String json;
        try { json = mapper.writeValueAsString(result); }
        catch (Exception ex) { throw new IllegalStateException("Impossible de conserver la confirmation de cet envoi", ex); }
        jdbc.update("INSERT INTO offline_attendance_receipts(user_id,operation_id,class_id,payload_hash,result_json) VALUES(?,?,?,?,?)",
                userId, request.operationId(), classId, hash, json);
        return result;
    }
    private String hash(Long classId, Batch request) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(List.of(classId, request)))); }
        catch (Exception ex) { throw new IllegalStateException("Impossible de vérifier l’envoi", ex); }
    }
    private String normalize(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
