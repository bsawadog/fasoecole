package org.afritechinnovations.dto.self;

import jakarta.validation.constraints.*;
import org.afritechinnovations.model.academic.AttendanceStatus;
import java.time.LocalDate;

public final class TeacherAttendanceDto {
    private TeacherAttendanceDto() {}
    public record Item(Long studentId, String fullName, String registrationNumber, AttendanceStatus status, String justification, Long version) {}
    public record Entry(@NotNull Long studentId,@NotNull AttendanceStatus status,@Size(max=500) String justification,AttendanceStatus expectedStatus) {}
    public record Batch(@NotNull @PastOrPresent LocalDate date,@NotEmpty @Size(max=2000) java.util.List<@jakarta.validation.Valid Entry> entries) {}
    public record Request(@NotNull @PastOrPresent LocalDate date, @NotNull AttendanceStatus status,
                          @Size(max=500) String justification) {}
}
