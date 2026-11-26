package com.school.attendance;

import com.school.attendance.AttendanceDtos.AttendanceSheet;
import com.school.attendance.AttendanceDtos.ClassReport;
import com.school.attendance.AttendanceDtos.SaveSheetRequest;
import com.school.attendance.AttendanceDtos.StudentSummary;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** Daily attendance sheet and reports. Which classes/students a caller may use is checked in AttendanceService. */
@RestController
@RequestMapping("/api/v1/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    @GetMapping("/sheet")
    public ResponseEntity<AttendanceSheet> getSheet(
            @RequestParam Long classId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ResponseEntity.ok(attendanceService.getSheet(classId, date));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    @PutMapping("/sheet")
    public ResponseEntity<AttendanceSheet> saveSheet(@Valid @RequestBody SaveSheetRequest request) {
        return ResponseEntity.ok(attendanceService.saveSheet(request));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    @GetMapping("/report")
    public ResponseEntity<ClassReport> classReport(
            @RequestParam Long classId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(attendanceService.classReport(classId, from, to));
    }

    /** Any login; who may see which student is decided in AttendanceService (StudentAccessGuard). */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/students/{studentId}")
    public ResponseEntity<StudentSummary> studentSummary(
            @PathVariable Long studentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(attendanceService.studentSummary(studentId, from, to));
    }
}
