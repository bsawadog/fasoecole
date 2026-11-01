package org.afritechinnovations.controler.communication;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.communication.FamilyContactDto;
import org.afritechinnovations.security.AccessGuard;
import org.afritechinnovations.service.communication.ConversationMessagingService;
import org.afritechinnovations.service.communication.MessageDeliveryService;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.CacheControl;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationMessagingController {
    private final ConversationMessagingService service;
    private final AccessGuard guard;
    private final MessageDeliveryService delivery;

    @GetMapping
    public List<FamilyContactDto.ConversationSummary> list(@RequestParam(required = false) Long schoolId) {
        return service.conversations(guard.currentUserId(), schoolId);
    }

    @GetMapping("/recipients")
    public List<FamilyContactDto.Recipient> recipients(@RequestParam Long schoolId,
                                                        @RequestParam(required = false) Long studentId) {
        return service.recipients(guard.currentUserId(), schoolId, studentId);
    }

    @GetMapping("/teacher/recipients")
    public List<FamilyContactDto.Recipient> teacherRecipients(@RequestParam Long schoolId,
            @RequestParam(required = false) Long classId) {
        return service.teacherRecipients(guard.currentUserId(), schoolId, classId);
    }

    @PostMapping(value = "/teacher/messages", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public List<FamilyContactDto.ConversationSummary> sendTeacherMessage(
            @Valid @RequestBody FamilyContactDto.TeacherMessageRequest request,
            @RequestHeader(value = "X-Offline-Message-Id", required = false) UUID operation) {
        Long user = guard.currentUserId();
        return delivery.deliver(user, operation, "teacher", request, List.of(), new TypeReference<>() {}, () -> service.sendTeacherMessage(user, request, List.of()));
    }

    @PostMapping(value = "/teacher/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public List<FamilyContactDto.ConversationSummary> sendTeacherMessageWithFiles(
            @Valid @RequestPart("request") FamilyContactDto.TeacherMessageRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            @RequestHeader(value = "X-Offline-Message-Id", required = false) UUID operation) {
        Long user = guard.currentUserId();
        return delivery.deliver(user, operation, "teacher", request, files, new TypeReference<>() {}, () -> service.sendTeacherMessage(user, request, files));
    }

    @GetMapping("/unread-count")
    public long unreadCount() { return service.unreadCount(guard.currentUserId()); }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public FamilyContactDto.ConversationThread start(@Valid @RequestBody FamilyContactDto.NewConversationRequest request,
            @RequestHeader(value = "X-Offline-Message-Id", required = false) UUID operation) {
        Long user = guard.currentUserId();
        return delivery.deliver(user, operation, "start", request, List.of(), new TypeReference<>() {}, () -> service.start(user, request));
    }

    @GetMapping("/{conversationId}")
    public FamilyContactDto.ConversationThread read(@PathVariable Long conversationId) {
        return service.read(guard.currentUserId(), conversationId);
    }

    @PostMapping(value = "/{conversationId}/messages", consumes = MediaType.APPLICATION_JSON_VALUE)
    public FamilyContactDto.ConversationThread reply(@PathVariable Long conversationId,
                                                      @Valid @RequestBody FamilyContactDto.ReplyRequest request,
            @RequestHeader(value = "X-Offline-Message-Id", required = false) UUID operation) {
        Long user = guard.currentUserId();
        return delivery.deliver(user, operation, "reply/" + conversationId, request, List.of(), new TypeReference<>() {}, () -> service.reply(user, conversationId, request));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public FamilyContactDto.ConversationThread startWithFiles(
            @Valid @RequestPart("request") FamilyContactDto.NewConversationRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            @RequestHeader(value = "X-Offline-Message-Id", required = false) UUID operation) {
        Long user = guard.currentUserId();
        return delivery.deliver(user, operation, "start", request, files, new TypeReference<>() {}, () -> service.startWithAttachments(user, request, files));
    }

    @PostMapping(value = "/{conversationId}/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FamilyContactDto.ConversationThread replyWithFiles(@PathVariable Long conversationId,
            @Valid @RequestPart("request") FamilyContactDto.ReplyRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            @RequestHeader(value = "X-Offline-Message-Id", required = false) UUID operation) {
        Long user = guard.currentUserId();
        return delivery.deliver(user, operation, "reply/" + conversationId, request, files, new TypeReference<>() {}, () -> service.replyWithAttachments(user, conversationId, request, files));
    }

    @GetMapping("/attachments/{attachmentId}")
    public ResponseEntity<byte[]> download(@PathVariable Long attachmentId) {
        var file = service.download(guard.currentUserId(), attachmentId);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.filename(), StandardCharsets.UTF_8).build().toString())
                .body(file.data());
    }
}
