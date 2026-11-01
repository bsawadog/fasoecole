package org.afritechinnovations.controler.self;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.afritechinnovations.security.AccessGuard;
import org.afritechinnovations.service.self.StudentDashboardService;
import org.afritechinnovations.service.self.StudentStudyService;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;
import org.afritechinnovations.service.communication.ActionDeliveryService;
@RestController @RequiredArgsConstructor @RequestMapping("/api/me/student")
public class StudentDashboardController {
 private final AccessGuard guard;private final StudentDashboardService dashboard;private final StudentStudyService study;
 private final ActionDeliveryService delivery;
 @GetMapping("/profiles") public List<StudentDashboardService.Profile> profiles(){return dashboard.profiles(guard.currentUserId());}
 @GetMapping("/dashboard") public StudentDashboardService.Dashboard overview(@RequestParam Long studentId){return dashboard.overview(guard.currentUserId(),studentId);}
 @GetMapping("/profiles/{studentId}/tasks") public List<StudentStudyService.Task> tasks(@PathVariable Long studentId){return study.list(guard.currentUserId(),studentId);}
 @PostMapping("/profiles/{studentId}/tasks") public void create(@PathVariable Long studentId,@Valid @RequestBody StudentStudyService.Request r,
  @RequestHeader(value="X-Offline-Action-Id",required=false) UUID operation){delivery.deliver(operation,"student/profiles/"+studentId+"/tasks",r,()->study.save(guard.currentUserId(),studentId,null,r));}
 @PutMapping("/profiles/{studentId}/tasks/{id}") public void update(@PathVariable Long studentId,@PathVariable Long id,@Valid @RequestBody StudentStudyService.Request r){study.save(guard.currentUserId(),studentId,id,r);}
 @DeleteMapping("/profiles/{studentId}/tasks/{id}") public void remove(@PathVariable Long studentId,@PathVariable Long id){study.remove(guard.currentUserId(),studentId,id);}
}
