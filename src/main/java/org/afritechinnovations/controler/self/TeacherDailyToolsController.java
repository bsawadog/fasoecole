package org.afritechinnovations.controler.self;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.afritechinnovations.security.AccessGuard;
import org.afritechinnovations.service.self.TeacherDailyToolsService;
import org.afritechinnovations.service.self.TeacherDailyToolsService.*;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;
import org.afritechinnovations.service.communication.ActionDeliveryService;

@RestController @RequiredArgsConstructor @RequestMapping("/api/me")
public class TeacherDailyToolsController {
 private final TeacherDailyToolsService service;private final AccessGuard guard;
 private final ActionDeliveryService delivery;
 @GetMapping("/teacher/daily/today") public Today today(){return service.today(guard.currentUserId());}
 @GetMapping("/teacher/classes/{classId}/lessons") public List<Lesson> lessons(@PathVariable Long classId){return service.lessons(guard.currentUserId(),classId);}
 @PostMapping("/teacher/classes/{classId}/lessons") public void lesson(@PathVariable Long classId,@Valid @RequestBody LessonRequest r,
  @RequestHeader(value="X-Offline-Action-Id",required=false) UUID operation){delivery.deliver(operation,"teacher/classes/"+classId+"/lessons",r,()->service.lesson(guard.currentUserId(),classId,null,r));}
 @PutMapping("/teacher/classes/{classId}/lessons/{id}") public void lesson(@PathVariable Long classId,@PathVariable Long id,@Valid @RequestBody LessonRequest r){service.lesson(guard.currentUserId(),classId,id,r);}
 @DeleteMapping("/teacher/classes/{classId}/lessons/{id}") public void removeLesson(@PathVariable Long classId,@PathVariable Long id){service.removeLesson(guard.currentUserId(),classId,id);}
 @GetMapping("/teacher/daily/notes") public List<Note> notes(){return service.notes(guard.currentUserId());}
 @PostMapping("/teacher/daily/notes") public void note(@Valid @RequestBody NoteRequest r,
  @RequestHeader(value="X-Offline-Action-Id",required=false) UUID operation){delivery.deliver(operation,"teacher/daily/notes",r,()->service.note(guard.currentUserId(),null,r));}
 @PutMapping("/teacher/daily/notes/{id}") public void note(@PathVariable Long id,@Valid @RequestBody NoteRequest r){service.note(guard.currentUserId(),id,r);}
 @DeleteMapping("/teacher/daily/notes/{id}") public void removeNote(@PathVariable Long id){service.removeNote(guard.currentUserId(),id);}
 @GetMapping("/teacher/classes/{classId}/support") public List<Support> support(@PathVariable Long classId){return service.support(guard.currentUserId(),classId);}
 @PutMapping("/teacher/classes/{classId}/support/{studentId}") public void support(@PathVariable Long classId,@PathVariable Long studentId,@Valid @RequestBody SupportRequest r){service.support(guard.currentUserId(),classId,studentId,r);}
 @GetMapping("/teacher/daily/availability") public List<Availability> availability(){return service.availability(guard.currentUserId());}
 @PostMapping("/teacher/daily/availability") public void availability(@Valid @RequestBody AvailabilityRequest r){service.availability(guard.currentUserId(),r);}
 @GetMapping("/students/{studentId}/learning") public FamilyLearning learning(@PathVariable Long studentId){return service.familyLearning(guard.currentUserId(),studentId);}
}
