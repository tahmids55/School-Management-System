package com.school.teacher;

import com.school.academic.ClassSubjectRepository;
import com.school.academic.SchoolClassRepository;
import com.school.audit.AuditService;
import com.school.common.PageResponse;
import com.school.common.exception.BusinessRuleException;
import com.school.common.exception.ConflictException;
import com.school.common.exception.NotFoundException;
import com.school.security.AccessGuard;
import com.school.teacher.TeacherDtos.ClassRef;
import com.school.teacher.TeacherDtos.CreatedTeacherResponse;
import com.school.teacher.TeacherDtos.StatusRequest;
import com.school.teacher.TeacherDtos.SubjectAssignment;
import com.school.teacher.TeacherDtos.TeacherRequest;
import com.school.teacher.TeacherDtos.TeacherResponse;
import com.school.teacher.TeacherDtos.TeachingLoad;
import com.school.user.AppUser;
import com.school.user.AppUserRepository;
import com.school.user.Role;
import com.school.user.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Staff records with a 1:1 login created in the same transaction.
 */
@Service
public class TeacherService {

    public static final String ENTITY_TYPE = "Teacher";
    public static final String AUDIT_TEACHER_CREATED = "TEACHER_CREATED";
    public static final String AUDIT_TEACHER_STATUS_CHANGED = "TEACHER_STATUS_CHANGED";

    private static final Sort DEFAULT_SORT = Sort.by(
            Sort.Order.asc("firstName"),
            Sort.Order.asc("lastName"),
            Sort.Order.asc("id")
    );

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "id", "employeeNo", "firstName", "lastName", "email",
            "phone", "designation", "joiningDate", "status", "createdAt", "updatedAt"
    );

    private final TeacherRepository teacherRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final ClassSubjectRepository classSubjectRepository;
    private final AccessGuard accessGuard;
    private final UserService userService;
    private final AppUserRepository appUserRepository;
    private final AuditService auditService;
    private final Clock clock;

    public TeacherService(
            TeacherRepository teacherRepository,
            SchoolClassRepository schoolClassRepository,
            ClassSubjectRepository classSubjectRepository,
            AccessGuard accessGuard,
            UserService userService,
            AppUserRepository appUserRepository,
            AuditService auditService,
            Clock clock
    ) {
        this.teacherRepository = teacherRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.classSubjectRepository = classSubjectRepository;
        this.accessGuard = accessGuard;
        this.userService = userService;
        this.appUserRepository = appUserRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<TeacherResponse> list(String query, TeacherStatus status, Pageable pageable) {
        Pageable effectivePageable = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), DEFAULT_SORT);
        validateSort(effectivePageable.getSort());

        String trimmed = query == null ? null : query.trim();
        Page<Teacher> page;
        if (trimmed != null && !trimmed.isEmpty()) {
            String pattern = toLikePattern(trimmed);
            page = status != null
                    ? teacherRepository.searchByStatusWithUser(status, pattern, effectivePageable)
                    : teacherRepository.searchWithUser(pattern, effectivePageable);
        } else {
            page = status != null
                    ? teacherRepository.findByStatusWithUser(status, effectivePageable)
                    : teacherRepository.findAllWithUser(effectivePageable);
        }

        return PageResponse.from(page, TeacherResponse::from);
    }

    @Transactional(readOnly = true)
    public TeacherResponse get(Long id) {
        return TeacherResponse.from(findWithUserOrThrow(id));
    }

    @Transactional(readOnly = true)
    public TeachingLoad teachingLoad(Long id) {
        if (!teacherRepository.existsById(id)) {
            throw new NotFoundException(ENTITY_TYPE, id);
        }
        return loadForTeacher(id);
    }

    @Transactional(readOnly = true)
    public TeachingLoad myTeachingLoad() {
        Teacher teacher = accessGuard.currentTeacher();
        return loadForTeacher(teacher.getId());
    }

    private TeachingLoad loadForTeacher(Long teacherId) {
        List<ClassRef> classTeacherOf = schoolClassRepository.findAllByClassTeacherIdWithYear(teacherId).stream()
                .map(ClassRef::from)
                .toList();
        List<SubjectAssignment> subjects = classSubjectRepository.findAllByTeacherIdWithDetails(teacherId).stream()
                .map(SubjectAssignment::from)
                .toList();
        return new TeachingLoad(classTeacherOf, subjects);
    }

    @Transactional
    public CreatedTeacherResponse create(TeacherRequest request) {
        String employeeNo = normalizeEmployeeNo(request.employeeNo());
        String firstName = request.firstName().trim();
        String lastName = request.lastName().trim();
        String email = normalizeEmail(request.email());
        String phone = request.phone().trim();
        String designation = request.designation().trim();
        validateJoiningDate(request.joiningDate());

        if (teacherRepository.existsByEmployeeNoIgnoreCase(employeeNo)) {
            throw new ConflictException("Employee number " + employeeNo + " already exists");
        }
        if (teacherRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Email " + email + " is already used by another teacher");
        }

        String username = employeeNo.toLowerCase(Locale.ROOT);
        String fullName = firstName + " " + lastName;
        UserService.NewAccount account = userService.createAccount(username, email, fullName, Role.TEACHER);

        Teacher teacher = new Teacher(
                account.user(),
                employeeNo,
                firstName,
                lastName,
                email,
                phone,
                designation,
                request.joiningDate()
        );
        Teacher saved = teacherRepository.saveAndFlush(teacher);

        auditService.record(
                AUDIT_TEACHER_CREATED,
                ENTITY_TYPE,
                String.valueOf(saved.getId()),
                "Created teacher " + saved.getEmployeeNo() + " (" + saved.getFullName() + ")"
        );
        return CreatedTeacherResponse.from(saved, account);
    }

    @Transactional
    public TeacherResponse update(Long id, TeacherRequest request) {
        Teacher teacher = findWithUserOrThrow(id);
        String firstName = request.firstName().trim();
        String lastName = request.lastName().trim();
        String email = normalizeEmail(request.email());
        String phone = request.phone().trim();
        String designation = request.designation().trim();
        validateJoiningDate(request.joiningDate());

        if (teacherRepository.existsByEmailIgnoreCaseAndIdNot(email, id)) {
            throw new ConflictException("Email " + email + " is already used by another teacher");
        }

        AppUser user = teacher.getUser();
        if (user != null) {
            if (!email.equalsIgnoreCase(user.getEmail()) && appUserRepository.existsByEmailIgnoreCase(email)) {
                throw new ConflictException("An account with email " + email + " already exists");
            }
            user.setEmail(email);
            user.setFullName(firstName + " " + lastName);
        }

        // Employee number stays immutable after creation.
        teacher.setFirstName(firstName);
        teacher.setLastName(lastName);
        teacher.setEmail(email);
        teacher.setPhone(phone);
        teacher.setDesignation(designation);
        teacher.setJoiningDate(request.joiningDate());

        return TeacherResponse.from(teacherRepository.saveAndFlush(teacher));
    }

    @Transactional
    public TeacherResponse updateStatus(Long id, StatusRequest request) {
        Teacher teacher = findWithUserOrThrow(id);
        TeacherStatus previous = teacher.getStatus();
        TeacherStatus next = request.status();

        teacher.setStatus(next);
        if (next == TeacherStatus.RESIGNED) {
            userService.disable(teacher.getUser());
        }

        Teacher saved = teacherRepository.saveAndFlush(teacher);
        auditService.record(
                AUDIT_TEACHER_STATUS_CHANGED,
                ENTITY_TYPE,
                String.valueOf(saved.getId()),
                "Changed status for " + saved.getEmployeeNo() + " from " + previous + " to " + next
        );
        return TeacherResponse.from(saved);
    }

    private Teacher findWithUserOrThrow(Long id) {
        return teacherRepository.findByIdWithUser(id)
                .orElseThrow(() -> new NotFoundException(ENTITY_TYPE, id));
    }

    private void validateJoiningDate(LocalDate joiningDate) {
        if (joiningDate.isAfter(LocalDate.now(clock))) {
            throw new BusinessRuleException("Joining date cannot be in the future");
        }
    }

    private static void validateSort(Sort sort) {
        for (Sort.Order order : sort) {
            if (!ALLOWED_SORT_FIELDS.contains(order.getProperty())) {
                throw new IllegalArgumentException("Invalid sort field: " + order.getProperty());
            }
        }
    }

    private static String normalizeEmployeeNo(String employeeNo) {
        return employeeNo.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String toLikePattern(String raw) {
        String escaped = raw.toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
    }
}
