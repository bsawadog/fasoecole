package org.afritechinnovations.controler.self;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.self.TeacherAttendanceDto;
import org.afritechinnovations.security.AccessGuard;
import org.afritechinnovations.service.self.OfflineAttendanceService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/me")
public class OfflineAttendanceController {
    private final AccessGuard guard;
    private final OfflineAttendanceService service;
    public record DeviceKey(String key) {}
    @PostMapping("/offline-devices/{deviceId}/key")
    public ResponseEntity<DeviceKey> key(@PathVariable UUID deviceId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new DeviceKey(service.deviceKey(guard.currentUserId(), deviceId)));
    }
    @PutMapping("/teacher/classes/{classId}/attendance/sync")
    public List<TeacherAttendanceDto.Item> synchronize(@PathVariable Long classId, @Valid @RequestBody OfflineAttendanceService.Batch request) {
        return service.synchronize(guard.currentUserId(), classId, request);
    }
}
