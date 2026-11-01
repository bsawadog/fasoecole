package org.afritechinnovations.controler.self;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.self.ParentPortalDto.*;
import org.afritechinnovations.service.self.ParentPortalService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.core.type.TypeReference;
import org.afritechinnovations.security.AccessGuard;
import org.afritechinnovations.service.communication.MessageDeliveryService;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ParentPortalController {
    private final ParentPortalService service;
    private final AccessGuard guard;
    private final MessageDeliveryService delivery;

    @GetMapping("/me/teacher/classes/{classId}/portal/posts")
    public List<Post> teacherPosts(@PathVariable Long classId,@RequestParam Kind kind) { return service.teacherPosts(classId,kind); }
    @PostMapping(value="/me/teacher/classes/{classId}/portal/posts",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Post teacherPublish(@PathVariable Long classId,@Valid @RequestPart("request") PostRequest request,
            @RequestPart(value="files",required=false) List<MultipartFile> files,
            @RequestHeader(value="X-Offline-Publication-Id",required=false) UUID operation) {
        return delivery.deliver(guard.currentUserId(), operation, "teacher-publication/" + classId, request, files,
                new TypeReference<Post>() {}, () -> service.teacherPublish(classId,request,files));
    }
    @DeleteMapping("/me/teacher/classes/{classId}/portal/posts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void teacherDelete(@PathVariable Long classId,@PathVariable Long id) { service.teacherDelete(classId,id); }
    @GetMapping("/me/teacher/classes/{classId}/portal/posts/{postId}/files/{fileId}")
    public ResponseEntity<byte[]> teacherFile(@PathVariable Long classId,@PathVariable Long postId,@PathVariable Long fileId) { return file(service.teacherDownload(classId,postId,fileId)); }
    @GetMapping("/me/teacher/classes/{classId}/portal/appointments")
    public List<Appointment> teacherAppointments(@PathVariable Long classId) { return service.teacherAppointments(classId); }
    @PostMapping("/me/teacher/classes/{classId}/portal/appointments/{id}/decision")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void teacherDecision(@PathVariable Long classId,@PathVariable Long id,@Valid @RequestBody AppointmentDecision request) { service.teacherDecide(classId,id,request); }

    @GetMapping("/me/parent-portal/students/{studentId}/posts")
    public List<Post> posts(@PathVariable Long studentId, @RequestParam Kind kind) { return service.studentPosts(studentId,kind); }
    @GetMapping("/me/parent-portal/students/{studentId}/payments")
    public List<PaymentItem> payments(@PathVariable Long studentId) { return service.payments(studentId); }
    @GetMapping("/me/parent-portal/students/{studentId}/evaluations")
    public List<EvaluationItem> evaluations(@PathVariable Long studentId) { return service.evaluations(studentId); }
    @GetMapping("/me/parent-portal/students/{studentId}/appointments")
    public List<Appointment> appointments(@PathVariable Long studentId) { return service.studentAppointments(studentId); }
    @PostMapping("/me/parent-portal/students/{studentId}/appointments")
    @ResponseStatus(HttpStatus.CREATED)
    public Appointment request(@PathVariable Long studentId, @Valid @RequestBody AppointmentRequest request) { return service.requestAppointment(studentId,request); }
    @PostMapping("/me/parent-portal/students/{studentId}/appointments/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable Long studentId,@PathVariable Long id) { service.cancelAppointment(studentId,id); }
    @GetMapping("/me/parent-portal/students/{studentId}/posts/{postId}/files/{fileId}")
    public ResponseEntity<byte[]> download(@PathVariable Long studentId,@PathVariable Long postId,@PathVariable Long fileId) {
        return file(service.studentDownload(studentId,postId,fileId));
    }
    @GetMapping("/owner/parent-portal/schools/{schoolId}/posts")
    public List<Post> schoolPosts(@PathVariable Long schoolId) { return service.schoolPosts(schoolId); }
    @GetMapping("/owner/parent-portal/schools/{schoolId}/students")
    public List<StudentTarget> schoolStudents(@PathVariable Long schoolId, @RequestParam Long classId) {
        return service.schoolStudents(schoolId, classId);
    }
    @PostMapping(value="/owner/parent-portal/schools/{schoolId}/posts",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Post publish(@PathVariable Long schoolId,@Valid @RequestPart("request") PostRequest request,
                        @RequestPart(value="files",required=false) List<MultipartFile> files) { return service.publish(schoolId,request,files); }
    @DeleteMapping("/owner/parent-portal/schools/{schoolId}/posts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long schoolId,@PathVariable Long id) { service.deletePost(schoolId,id); }
    @GetMapping("/owner/parent-portal/schools/{schoolId}/posts/{postId}/files/{fileId}")
    public ResponseEntity<byte[]> schoolDownload(@PathVariable Long schoolId,@PathVariable Long postId,@PathVariable Long fileId) {
        return file(service.schoolDownload(schoolId,postId,fileId));
    }
    @GetMapping("/owner/parent-portal/schools/{schoolId}/appointments")
    public List<Appointment> schoolAppointments(@PathVariable Long schoolId) { return service.schoolAppointments(schoolId); }
    @PostMapping("/owner/parent-portal/schools/{schoolId}/appointments/{id}/decision")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void decision(@PathVariable Long schoolId,@PathVariable Long id,@Valid @RequestBody AppointmentDecision request) {
        service.decideAppointment(schoolId,id,request);
    }
    private ResponseEntity<byte[]> file(Download file) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(file.filename(),StandardCharsets.UTF_8).build().toString())
                .body(file.data());
    }
}
