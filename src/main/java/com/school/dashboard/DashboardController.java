package com.school.dashboard;

import com.school.dashboard.DashboardDtos.AdminDashboard;
import com.school.dashboard.DashboardDtos.StudentDashboard;
import com.school.dashboard.DashboardDtos.TeacherDashboard;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** One request per role: the facade assembles everything the dashboard shows. */
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardFacade dashboardFacade;

    public DashboardController(DashboardFacade dashboardFacade) {
        this.dashboardFacade = dashboardFacade;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin")
    public ResponseEntity<AdminDashboard> admin() {
        return ResponseEntity.ok(dashboardFacade.adminDashboard());
    }

    @PreAuthorize("hasRole('TEACHER')")
    @GetMapping("/teacher")
    public ResponseEntity<TeacherDashboard> teacher() {
        return ResponseEntity.ok(dashboardFacade.teacherDashboard());
    }

    /** The logged-in student's own home; whose data it is comes from the token, never from the URL. */
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/student")
    public ResponseEntity<StudentDashboard> student() {
        return ResponseEntity.ok(dashboardFacade.studentDashboard());
    }
}
