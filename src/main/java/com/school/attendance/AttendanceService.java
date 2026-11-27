package com.school.attendance;

import com.school.academic.AcademicYear;
import com.school.academic.SchoolClass;
import com.school.academic.SchoolClassRepository;
import com.school.attendance.AttendanceDtos.AttendanceSheet;
import com.school.attendance.AttendanceDtos.ClassReport;
import com.school.attendance.AttendanceDtos.Entry;
import com.school.attendance.AttendanceDtos.SaveSheetRequest;
import com.school.attendance.AttendanceDtos.SheetRow;
import com.school.attendance.AttendanceDtos.StudentAttendance;
import com.school.attendance.AttendanceDtos.StudentSummary;
import com.school.audit.AuditService;
import com.school.common.exception.BusinessRuleException;
import com.school.common.exception.NotFoundException;
import com.school.security.AccessGuard;
import com.school.security.StudentAccessGuard;
import com.school.student.Student;
import com.school.student.StudentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Daily attendance of a class (F19) and attendance reports (F20).
 * Admins may use every class; a teacher only a class they lead or teach a subject in (AccessGuard).
 */
@Service
public class AttendanceService {

    public static final String AUDIT_ATTENDANCE_SAVED = "ATTENDANCE_SAVED";

    private final SchoolClassRepository schoolClassRepository;
    private final StudentRepository studentRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final AccessGuard accessGuard;
    private final StudentAccessGuard studentAccessGuard;
    private final AuditService auditService;
    private final Clock clock;
    private final int lowThreshold;

    public AttendanceService(
            SchoolClassRepository schoolClassRepository,
            StudentRepository studentRepository,
            AttendanceRecordRepository attendanceRecordRepository,
            AccessGuard accessGuard,
            StudentAccessGuard studentAccessGuard,
            AuditService auditService,
            Clock clock,
            @Value("${app.attendance.low-threshold-percent:75}") int lowThreshold
    ) {
        this.schoolClassRepository = schoolClassRepository;
        this.studentRepository = studentRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.accessGuard = accessGuard;
        this.studentAccessGuard = studentAccessGuard;
        this.auditService = auditService;
        this.clock = clock;
        this.lowThreshold = lowThreshold;
    }

    /**
     * Percentages of every active student of a class between two dates (both included).
     * Always 4 queries, whatever the class size: class, roster, grouped counts, days taken.
     */
    @Transactional(readOnly = true)
    public ClassReport classReport(Long classId, LocalDate from, LocalDate to) {
        SchoolClass schoolClass = loadManageableClass(classId);
        checkRange(from, to);

        List<Student> roster = studentRepository.findActiveRoster(classId);
        // studentId -> (status -> how many days)
        Map<Long, Map<AttendanceStatus, Long>> countsByStudent = new HashMap<>();
        for (StatusCount row : attendanceRecordRepository.countByStudentAndStatus(classId, from, to)) {
            countsByStudent
                    .computeIfAbsent(row.studentId(), id -> new EnumMap<>(AttendanceStatus.class))
                    .put(row.status(), row.count());
        }
        long daysTaken = attendanceRecordRepository.countDaysTaken(classId, from, to);

        List<StudentAttendance> students = roster.stream()
                .map(student -> StudentAttendance.from(
                        student, countsByStudent.getOrDefault(student.getId(), Map.of()), lowThreshold))
                .toList();
        return new ClassReport(
                schoolClass.getId(), schoolClass.getDisplayName(), from, to, daysTaken, lowThreshold, students);
    }

    /** Totals and the days (newest first) of one student. ADMIN, own TEACHER, or the student themself. */
    @Transactional(readOnly = true)
    public StudentSummary studentSummary(Long studentId, LocalDate from, LocalDate to) {
        studentAccessGuard.checkCanViewStudent(studentId);
        checkRange(from, to);
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new NotFoundException("Student", studentId));
        List<AttendanceRecord> records = attendanceRecordRepository
                .findByStudentIdAndAttendanceDateBetweenOrderByAttendanceDateDesc(studentId, from, to);
        return StudentSummary.from(student, from, to, records, lowThreshold);
    }

    private static void checkRange(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new BusinessRuleException("'to' date cannot be before 'from' date");
        }
    }

    /** Active students by roll, with the status saved for that day (null when not saved yet). */
    @Transactional(readOnly = true)
    public AttendanceSheet getSheet(Long classId, LocalDate date) {
        SchoolClass schoolClass = loadManageableClass(classId);
        List<Student> roster = studentRepository.findActiveRoster(classId);
        Map<Long, AttendanceRecord> records = loadRecordsByStudent(date, roster);
        return toSheet(schoolClass, date, roster, records);
    }

    /**
     * Saves (or corrects) one day. Every row is checked first; if one is wrong nothing is saved.
     * The method is one transaction, so a failure while writing also rolls back every row.
     */
    @Transactional
    public AttendanceSheet saveSheet(SaveSheetRequest request) {
        SchoolClass schoolClass = loadManageableClass(request.classId());
        LocalDate date = request.date();

        // "Today" is the school's day (Asia/Dhaka) from the injected Clock, never the server's time zone.
        LocalDate today = LocalDate.now(clock);
        if (date.isAfter(today)) {
            throw new BusinessRuleException("Attendance cannot be taken for a future date");
        }
        AcademicYear year = schoolClass.getAcademicYear();
        if (!year.contains(date)) {
            throw new BusinessRuleException("The date is outside academic year " + year.getName());
        }

        List<Student> roster = studentRepository.findActiveRoster(schoolClass.getId());
        Map<Long, Student> rosterById = roster.stream()
                .collect(Collectors.toMap(Student::getId, Function.identity()));

        // Phase 1: validate every row before writing anything.
        Set<Long> seen = new HashSet<>();
        for (Entry entry : request.entries()) {
            if (!seen.add(entry.studentId())) {
                throw new BusinessRuleException("Duplicate student " + entry.studentId() + " in attendance sheet");
            }
            if (!rosterById.containsKey(entry.studentId())) {
                throw new BusinessRuleException(
                        "Student " + entry.studentId() + " is not an active student of this class");
            }
        }

        // Phase 2: correct existing rows or create new ones, then save them all together.
        Map<Long, AttendanceRecord> records = loadRecordsByStudent(date, roster);
        List<AttendanceRecord> toSave = new ArrayList<>(request.entries().size());
        for (Entry entry : request.entries()) {
            String remarks = normalizeRemarks(entry.remarks());
            AttendanceRecord record = records.get(entry.studentId());
            if (record == null) {
                record = new AttendanceRecord(
                        rosterById.get(entry.studentId()), schoolClass, date, entry.status(), remarks);
                records.put(entry.studentId(), record);
            } else {
                record.correct(schoolClass, entry.status(), remarks);
            }
            toSave.add(record);
        }
        attendanceRecordRepository.saveAllAndFlush(toSave);

        auditService.record(
                AUDIT_ATTENDANCE_SAVED,
                "SchoolClass",
                String.valueOf(schoolClass.getId()),
                "Saved " + toSave.size() + " attendance records for " + schoolClass.getDisplayName() + " on " + date
        );
        return toSheet(schoolClass, date, roster, records);
    }

    /** 404 when the class does not exist, 403 when the teacher does not teach in it. */
    private SchoolClass loadManageableClass(Long classId) {
        accessGuard.checkCanManageClass(classId);
        return schoolClassRepository.findByIdWithYear(classId)
                .orElseThrow(() -> new NotFoundException("Class", classId));
    }

    /** The day's records of the roster students, in one query, keyed by student id. */
    private Map<Long, AttendanceRecord> loadRecordsByStudent(LocalDate date, List<Student> roster) {
        Map<Long, AttendanceRecord> byStudent = new HashMap<>();
        if (roster.isEmpty()) {
            return byStudent;
        }
        List<Long> studentIds = roster.stream().map(Student::getId).toList();
        for (AttendanceRecord record : attendanceRecordRepository.findByDateAndStudentIds(date, studentIds)) {
            byStudent.put(record.getStudent().getId(), record);
        }
        return byStudent;
    }

    private AttendanceSheet toSheet(
            SchoolClass schoolClass,
            LocalDate date,
            List<Student> roster,
            Map<Long, AttendanceRecord> records
    ) {
        boolean taken = attendanceRecordRepository.existsBySchoolClassIdAndAttendanceDate(schoolClass.getId(), date);
        List<SheetRow> rows = roster.stream()
                .map(student -> SheetRow.from(student, records.get(student.getId())))
                .toList();
        return new AttendanceSheet(schoolClass.getId(), schoolClass.getDisplayName(), date, taken, rows);
    }

    /** Trimmed; blank becomes null. */
    private static String normalizeRemarks(String remarks) {
        if (remarks == null || remarks.isBlank()) {
            return null;
        }
        return remarks.trim();
    }
}
