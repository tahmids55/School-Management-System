package com.school.student;

import com.school.academic.SchoolClass;
import com.school.academic.SchoolClassRepository;
import com.school.audit.AuditService;
import com.school.common.PageResponse;
import com.school.common.SecurityUtils;
import com.school.common.exception.BusinessRuleException;
import com.school.common.exception.ConflictException;
import com.school.common.exception.NotFoundException;
import com.school.security.StudentAccessGuard;
import com.school.student.StudentDtos.CreatedStudentResponse;
import com.school.student.StudentDtos.StatusRequest;
import com.school.student.StudentDtos.StudentRequest;
import com.school.student.StudentDtos.StudentResponse;
import com.school.student.StudentDtos.StudentSummary;
import com.school.user.Role;
import com.school.user.UserDtos.TemporaryPasswordResponse;
import com.school.user.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Student records: search, admit, edit, place in a class with a roll number, and change status.
 * There is no delete: a non-ACTIVE status is the soft delete (the row and its history stay).
 */
@Service
public class StudentService {

    static final String ENTITY_TYPE = "Student";
    public static final String AUDIT_ADMITTED = "STUDENT_ADMITTED";
    public static final String AUDIT_STATUS_CHANGED = "STUDENT_STATUS_CHANGED";

    private static final Sort EXPORT_SORT = Sort.by("admissionNo");

    private final StudentRepository studentRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final UserService userService;
    private final AuditService auditService;
    private final StudentCsvExporter csvExporter;
    private final StudentAccessGuard accessGuard;

    public StudentService(
            StudentRepository studentRepository,
            SchoolClassRepository schoolClassRepository,
            UserService userService,
            AuditService auditService,
            StudentCsvExporter csvExporter,
            StudentAccessGuard accessGuard
    ) {
        this.studentRepository = studentRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.userService = userService;
        this.auditService = auditService;
        this.csvExporter = csvExporter;
        this.accessGuard = accessGuard;
    }

    /**
     * One page of students matching only the filters that were given. Always 2 queries: page + count.
     * A TEACHER only ever gets students of their own classes: the restriction is part of the SQL,
     * so it also applies when another classId is asked for. No classes = an empty page.
     */
    @Transactional(readOnly = true)
    public PageResponse<StudentSummary> search(String q, Long classId, StudentStatus status, Pageable pageable) {
        Specification<Student> spec = StudentSpecifications.search(q, classId, status);
        if (!SecurityUtils.hasRole("ADMIN")) {
            Set<Long> ownClasses = accessGuard.teacherClassIds();
            if (ownClasses.isEmpty()) {
                return PageResponse.from(Page.<StudentSummary>empty(pageable));
            }
            spec = spec.and(StudentSpecifications.inClasses(ownClasses));
        }
        Page<Student> page = studentRepository.findAll(spec, pageable);
        return PageResponse.from(page, StudentSummary::from);
    }

    /** Every matching student (not paged) as CSV, ordered by admission no. */
    @Transactional(readOnly = true)
    public String exportCsv(Long classId, StudentStatus status) {
        List<Student> students = studentRepository.findAll(
                StudentSpecifications.search(null, classId, status), EXPORT_SORT);
        return csvExporter.toCsv(students);
    }

    /** The ownership check runs first, so a teacher or student cannot even learn whether an id exists. */
    @Transactional(readOnly = true)
    public StudentResponse get(Long id) {
        accessGuard.checkCanViewStudent(id);
        return StudentResponse.from(findStudent(id));
    }

    /** Active students of a class, ordered by roll. A teacher only for their own classes. */
    @Transactional(readOnly = true)
    public List<StudentSummary> roster(Long classId) {
        accessGuard.checkCanViewClass(classId);
        if (!schoolClassRepository.existsById(classId)) {
            throw new NotFoundException("Class", classId);
        }
        return studentRepository.findActiveRoster(classId).stream().map(StudentSummary::from).toList();
    }

    /**
     * Admits a new ACTIVE student. This is the ONLY way to register a student (no self-registration).
     * With createAccount, a STUDENT login (username = admission no in lower case) is created and linked
     * in the same transaction: if anything fails, neither the student nor the login is saved.
     */
    @Transactional
    public CreatedStudentResponse create(StudentRequest request) {
        String admissionNo = normalizeAdmissionNo(request.admissionNo());
        if (studentRepository.existsByAdmissionNo(admissionNo)) {
            throw new ConflictException("Admission number " + admissionNo + " already exists");
        }
        String email = normalizeEmail(request.email());
        if (request.createAccount() && email == null) {
            throw new BusinessRuleException("An email address is required to create a student login");
        }
        if (email != null && studentRepository.existsByEmail(email)) {
            throw new ConflictException("Email " + email + " is already used by another student");
        }
        Student student = new Student(admissionNo);
        copyDetails(student, request, email);
        place(student, request.classId(), request.rollNo());

        UserService.NewAccount account = null;
        if (request.createAccount()) {
            account = userService.createAccount(
                    admissionNo.toLowerCase(Locale.ROOT), email, student.getFullName(), Role.STUDENT);
            student.setUser(account.user());
        }
        Student saved = studentRepository.saveAndFlush(student);
        auditService.record(AUDIT_ADMITTED, ENTITY_TYPE, String.valueOf(saved.getId()),
                "Admitted " + label(saved) + (saved.getSchoolClass() == null
                        ? " without a class"
                        : " to " + saved.getSchoolClass().getDisplayName())
                        + (account == null ? "" : " with login " + account.user().getUsername()));
        // The temporary password is in this response only; it is never stored or shown again.
        return CreatedStudentResponse.from(StudentResponse.from(saved),
                account == null ? null : TemporaryPasswordResponse.from(account));
    }

    /** Edits details and placement. The admission no in the request is ignored: it never changes. */
    @Transactional
    public StudentResponse update(Long id, StudentRequest request) {
        Student student = findStudent(id);
        String email = normalizeEmail(request.email());
        if (email != null && studentRepository.existsByEmailAndIdNot(email, id)) {
            throw new ConflictException("Email " + email + " is already used by another student");
        }
        copyDetails(student, request, email);
        place(student, request.classId(), request.rollNo());
        return StudentResponse.from(studentRepository.saveAndFlush(student));
    }

    /**
     * The soft delete. A non-ACTIVE status disables the student's login (if any);
     * becoming ACTIVE again needs a free seat in the student's class.
     */
    @Transactional
    public StudentResponse changeStatus(Long id, StatusRequest request) {
        Student student = findStudent(id);
        StudentStatus oldStatus = student.getStatus();
        StudentStatus newStatus = request.status();
        if (oldStatus == newStatus) {
            return StudentResponse.from(student);
        }
        if (newStatus == StudentStatus.ACTIVE && student.getSchoolClass() != null) {
            checkSeatFree(lockClass(student.getSchoolClass().getId()));
        }
        student.setStatus(newStatus);
        if (newStatus != StudentStatus.ACTIVE) {
            userService.disable(student.getUser());
        }
        Student saved = studentRepository.saveAndFlush(student);
        auditService.record(AUDIT_STATUS_CHANGED, ENTITY_TYPE, String.valueOf(id),
                label(saved) + ": " + oldStatus + " -> " + newStatus);
        return StudentResponse.from(saved);
    }

    /**
     * Puts the student in a class with a roll number. No class means no roll.
     * Joining a class needs a free seat (active students < capacity); the default roll is highest + 1.
     */
    private void place(Student student, Long classId, Integer requestedRoll) {
        if (classId == null) {
            student.setSchoolClass(null);
            student.setRollNo(null);
            return;
        }
        SchoolClass current = student.getSchoolClass();
        boolean joining = current == null || !current.getId().equals(classId);
        SchoolClass schoolClass = joining ? lockClass(classId) : current;
        if (joining && student.isActive()) {
            checkSeatFree(schoolClass);
        }
        Integer roll = requestedRoll;
        if (roll == null) {
            roll = joining ? nextRollNo(classId) : student.getRollNo();
        }
        boolean taken = student.getId() == null
                ? studentRepository.existsBySchoolClassIdAndRollNo(classId, roll)
                : studentRepository.existsBySchoolClassIdAndRollNoAndIdNot(classId, roll, student.getId());
        if (taken) {
            throw new ConflictException("Roll number " + roll + " is already taken in " + schoolClass.getDisplayName());
        }
        student.setSchoolClass(schoolClass);
        student.setRollNo(roll);
    }

    /**
     * Loads the class with a row lock (SELECT ... FOR UPDATE) until the transaction ends,
     * so two admins cannot both take the last free seat at the same moment.
     */
    private SchoolClass lockClass(Long classId) {
        return schoolClassRepository.findByIdForUpdate(classId)
                .orElseThrow(() -> new NotFoundException("Class", classId));
    }

    private void checkSeatFree(SchoolClass schoolClass) {
        long active = studentRepository.countBySchoolClassIdAndStatus(schoolClass.getId(), StudentStatus.ACTIVE);
        if (active >= schoolClass.getCapacity()) {
            throw new ConflictException(
                    schoolClass.getDisplayName() + " is full (capacity " + schoolClass.getCapacity() + ")");
        }
    }

    private int nextRollNo(Long classId) {
        Integer highest = studentRepository.findMaxRollNo(classId);
        return highest == null ? 1 : highest + 1;
    }

    private static void copyDetails(Student student, StudentRequest request, String email) {
        student.setFirstName(request.firstName().trim());
        student.setLastName(request.lastName().trim());
        student.setGender(request.gender());
        student.setDateOfBirth(request.dateOfBirth());
        student.setEmail(email);
        student.setPhone(blankToNull(request.phone()));
        student.setAddress(blankToNull(request.address()));
        student.setGuardianName(request.guardianName().trim());
        student.setGuardianPhone(request.guardianPhone().trim());
        student.setGuardianRelation(request.guardianRelation().trim());
        student.setAdmissionDate(request.admissionDate());
    }

    private Student findStudent(Long id) {
        return studentRepository.findByIdWithClassAndUser(id)
                .orElseThrow(() -> new NotFoundException("Student", id));
    }

    private static String label(Student student) {
        return student.getFullName() + " (" + student.getAdmissionNo() + ")";
    }

    static String normalizeAdmissionNo(String admissionNo) {
        return admissionNo.trim().toUpperCase(Locale.ROOT);
    }

    /** Lower-case; blank means "no email". */
    static String normalizeEmail(String email) {
        String value = blankToNull(email);
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
