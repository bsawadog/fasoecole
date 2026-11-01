package org.afritechinnovations.controler.self;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.self.SchoolLifeDto.*;
import org.afritechinnovations.service.self.SchoolLifeService;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.util.UUID;
import org.afritechinnovations.service.communication.ActionDeliveryService;

@RestController
@RequestMapping("/api/school-life")
@RequiredArgsConstructor
public class SchoolLifeController {
    private final SchoolLifeService service;
    private final ActionDeliveryService delivery;
    @GetMapping("/schools/{schoolId}")
    public Overview school(@PathVariable Long schoolId,@RequestParam(required=false) LifeModule module) { return service.ownerOverview(schoolId,module); }
    @GetMapping("/classes/{classId}")
    public Overview teacher(@PathVariable Long classId,@RequestParam(required=false) LifeModule module) { return service.teacherOverview(classId,module); }
    @GetMapping("/students/{studentId}")
    public Overview student(@PathVariable Long studentId,@RequestParam(required=false) LifeModule module) { return service.studentOverview(studentId,module); }

    @PostMapping("/schools/{schoolId}/events") @ResponseStatus(HttpStatus.CREATED)
    public void event(@PathVariable Long schoolId,@Valid @RequestBody EventRequest request,
            @RequestHeader(value="X-Offline-Action-Id",required=false) UUID operation) {
        delivery.deliver(operation,"schools/"+schoolId+"/events",request,()->service.createEvent(schoolId,request));
    }
    @DeleteMapping("/schools/{schoolId}/events/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEvent(@PathVariable Long schoolId,@PathVariable Long id) { service.deleteEvent(schoolId,id); }

    @PostMapping("/schools/{schoolId}/observations") @ResponseStatus(HttpStatus.CREATED)
    public void observation(@PathVariable Long schoolId,@Valid @RequestBody ObservationRequest request,
            @RequestHeader(value="X-Offline-Action-Id",required=false) UUID operation) {
        delivery.deliver(operation,"schools/"+schoolId+"/observations",request,()->service.ownerObservation(schoolId,request));
    }
    @PostMapping("/classes/{classId}/observations") @ResponseStatus(HttpStatus.CREATED)
    public void teacherObservation(@PathVariable Long classId,@Valid @RequestBody ObservationRequest request,
            @RequestHeader(value="X-Offline-Action-Id",required=false) UUID operation) {
        delivery.deliver(operation,"classes/"+classId+"/observations",request,()->service.teacherObservation(classId,request));
    }
    @PutMapping("/schools/{schoolId}/observations/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resolve(@PathVariable Long schoolId,@PathVariable Long id,@Valid @RequestBody ObservationDecision request) { service.resolveObservation(schoolId,id,request); }

    @PostMapping("/students/{studentId}/requests") @ResponseStatus(HttpStatus.CREATED)
    public void document(@PathVariable Long studentId,@Valid @RequestBody DocumentRequest request,
            @RequestHeader(value="X-Offline-Action-Id",required=false) UUID operation) {
        delivery.deliver(operation,"students/"+studentId+"/requests",request,()->service.requestDocument(studentId,request));
    }
    @PutMapping("/schools/{schoolId}/requests/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void decision(@PathVariable Long schoolId,@PathVariable Long id,@Valid @RequestBody DocumentDecision request) { service.decideDocument(schoolId,id,request); }
    @PostMapping("/students/{studentId}/requests/{id}/cancel") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable Long studentId,@PathVariable Long id) { service.cancelDocument(studentId,id); }

    @PostMapping("/schools/{schoolId}/books") @ResponseStatus(HttpStatus.CREATED)
    public void book(@PathVariable Long schoolId,@Valid @RequestBody BookRequest request,
            @RequestHeader(value="X-Offline-Action-Id",required=false) UUID operation) {
        delivery.deliver(operation,"schools/"+schoolId+"/books",request,()->service.createBook(schoolId,request));
    }
    @PostMapping("/schools/{schoolId}/loans") @ResponseStatus(HttpStatus.CREATED)
    public void lend(@PathVariable Long schoolId,@Valid @RequestBody LoanRequest request) { service.lend(schoolId,request); }
    @PostMapping("/schools/{schoolId}/loans/{id}/return") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void returnBook(@PathVariable Long schoolId,@PathVariable Long id) { service.returnBook(schoolId,id); }

    @PutMapping("/schools/{schoolId}/homeworks/{postId}/students/{studentId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void homework(@PathVariable Long schoolId,@PathVariable Long postId,@PathVariable Long studentId,@Valid @RequestBody HomeworkDecision request) { service.ownerHomework(schoolId,postId,studentId,request); }
    @PutMapping("/classes/{classId}/homeworks/{postId}/students/{studentId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void teacherHomework(@PathVariable Long classId,@PathVariable Long postId,@PathVariable Long studentId,@Valid @RequestBody HomeworkDecision request) { service.teacherHomework(classId,postId,studentId,request); }
}
