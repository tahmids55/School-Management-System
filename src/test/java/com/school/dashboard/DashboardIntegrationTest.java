package com.school.dashboard;

import com.school.IntegrationTestBase;
import com.school.academic.AcademicYear;
import com.school.academic.AcademicYearRepository;
import com.school.academic.ClassSubject;
import com.school.academic.ClassSubjectRepository;
import com.school.academic.SchoolClass;
import com.school.academic.SchoolClassRepository;
import com.school.academic.Subject;
import com.school.academic.SubjectRepository;
import com.school.attendance.AttendanceRecord;
import com.school.attendance.AttendanceRecordRepository;
import com.school.attendance.AttendanceStatus;
import com.school.exam.Exam;
import com.school.exam.ExamRepository;
import com.school.exam.Mark;
import com.school.exam.MarkRepository;
import com.school.fee.FeeInvoice;
import com.school.fee.FeeInvoiceRepository;
import com.school.fee.Payment;
import com.school.fee.PaymentMethod;
import com.school.fee.PaymentRepository;
import com.school.notice.Notice;
import com.school.notice.NoticeAudience;
import com.school.notice.NoticeRepository;
import com.school.student.Gender;
import com.school.student.Student;
import com.school.student.StudentRepository;
import com.school.student.StudentStatus;
import com.school.teacher.Teacher;
import com.school.teacher.TeacherRepository;
import com.school.teacher.TeacherStatus;
import com.school.user.AppUser;
import com.school.user.AppUserRepository;
import com.school.user.Role;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F25 dashboards through the real API and database, on a clock fixed at 10:00 on 27 Nov 2026 in Dhaka.
 * The numbers below are worked out by hand from the seeded rows.
 */
class DashboardIntegrationTest extends IntegrationTestBase {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 27);

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedDhakaClock() {
            return Clock.fixed(Instant.parse("2026-11-27T04:00:00Z"), ZoneId.of("Asia/Dhaka"));
        }
    }

    @Autowired
    private AppUserRepository appUserRepository;
    @Autowired
    private AcademicYearRepository academicYearRepository;
    @Autowired
    private SchoolClassRepository schoolClassRepository;
    @Autowired
    private SubjectRepository subjectRepository;
    @Autowired
    private ClassSubjectRepository classSubjectRepository;
    @Autowired
    private TeacherRepository teacherRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private AttendanceRecordRepository attendanceRecordRepository;
    @Autowired
    private FeeInvoiceRepository feeInvoiceRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private ExamRepository examRepository;
    @Autowired
    private MarkRepository markRepository;
    @Autowired
    private NoticeRepository noticeRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        appUserRepository.save(new AppUser(
                "admin", "admin@school.edu", passwordEncoder.encode("AdminPass#1"), "Nasima Akter", Role.ADMIN));
        appUserRepository.flush();
        adminToken = login("admin", "AdminPass#1");
    }

    @Test
    void emptyDatabase_adminDashboard_isZerosAndEmptyLists() throws Exception {
        getAdmin(adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.academicYear").doesNotExist())
                .andExpect(jsonPath("$.counts.students").value(0))
                .andExpect(jsonPath("$.counts.teachers").value(0))
                .andExpect(jsonPath("$.counts.classes").value(0))
                .andExpect(jsonPath("$.counts.subjects").value(0))
                .andExpect(jsonPath("$.todayAttendance").doesNotExist())
                .andExpect(jsonPath("$.attendanceTrend.length()").value(0))
                .andExpect(jsonPath("$.fees.totalBilled").value(0))
                .andExpect(jsonPath("$.fees.overdueInvoices").value(0))
                .andExpect(jsonPath("$.monthlyCollection.length()").value(6))
                .andExpect(jsonPath("$.monthlyCollection[5].month").value("Nov 2026"))
                .andExpect(jsonPath("$.monthlyCollection[5].amount").value(0))
                .andExpect(jsonPath("$.classStrength.length()").value(0))
                .andExpect(jsonPath("$.genderSplit.length()").value(0))
                .andExpect(jsonPath("$.latestExam").doesNotExist())
                .andExpect(jsonPath("$.gradeDistribution.length()").value(0))
                .andExpect(jsonPath("$.recentPayments.length()").value(0))
                .andExpect(jsonPath("$.notices.length()").value(0));
    }

    @Test
    void adminDashboard_assemblesTheRealNumbers() throws Exception {
        School school = seedSchool();

        getAdmin(adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.academicYear").value("2026"))
                // 3 active (Chandni transferred); 2 active teachers (Rina on leave); 2 classes of 2026; 2 subjects
                .andExpect(jsonPath("$.counts.students").value(3))
                .andExpect(jsonPath("$.counts.teachers").value(2))
                .andExpect(jsonPath("$.counts.classes").value(2))
                .andExpect(jsonPath("$.counts.subjects").value(2))
                // today: Amina present, Babul absent -> 50.0
                .andExpect(jsonPath("$.todayAttendance").value(50.0))
                // 20 days ago is outside the 14 days; yesterday: present + late (+ excused not counted) = 100.0
                .andExpect(jsonPath("$.attendanceTrend.length()").value(2))
                .andExpect(jsonPath("$.attendanceTrend[0].date").value("2026-11-26"))
                .andExpect(jsonPath("$.attendanceTrend[0].percentage").value(100.0))
                .andExpect(jsonPath("$.attendanceTrend[1].date").value("2026-11-27"))
                .andExpect(jsonPath("$.attendanceTrend[1].percentage").value(50.0))
                // billed 1000 + 500, collected 400 + 500; Amina's invoice is overdue
                .andExpect(jsonPath("$.fees.totalBilled").value(1500.00))
                .andExpect(jsonPath("$.fees.totalCollected").value(900.00))
                .andExpect(jsonPath("$.fees.overdueInvoices").value(1))
                // Jun..Nov; Sep 500; the 400 paid at 00:30 on 1 Nov Dhaka (31 Oct UTC) counts for November
                .andExpect(jsonPath("$.monthlyCollection[*].month").value(contains(
                        "Jun 2026", "Jul 2026", "Aug 2026", "Sep 2026", "Oct 2026", "Nov 2026")))
                .andExpect(jsonPath("$.monthlyCollection[*].amount").value(contains(
                        0.0, 0.0, 0.0, 500.0, 0.0, 400.0)))
                .andExpect(jsonPath("$.classStrength[*].name").value(contains("Class 9 - A", "Class 9 - B")))
                .andExpect(jsonPath("$.classStrength[*].value").value(contains(2, 1)))
                .andExpect(jsonPath("$.genderSplit[*].name").value(contains("Male", "Female")))
                .andExpect(jsonPath("$.genderSplit[*].value").value(contains(2, 1)))
                // newest published exam; Amina A (GPA 4.50), Babul F (failed math), Dipu A+
                .andExpect(jsonPath("$.latestExam").value("Half Yearly"))
                .andExpect(jsonPath("$.gradeDistribution[*].name")
                        .value(contains("A+", "A", "A-", "B", "C", "D", "F")))
                .andExpect(jsonPath("$.gradeDistribution[*].value").value(contains(1, 1, 0, 0, 0, 0, 1)))
                // newest payment first
                .andExpect(jsonPath("$.recentPayments.length()").value(2))
                .andExpect(jsonPath("$.recentPayments[0].studentName").value("Amina Islam"))
                .andExpect(jsonPath("$.recentPayments[0].invoiceNo").value(school.aminaInvoiceNo))
                .andExpect(jsonPath("$.recentPayments[0].amount").value(400.00))
                .andExpect(jsonPath("$.recentPayments[0].method").value("CASH"))
                .andExpect(jsonPath("$.recentPayments[0].paidAt").value("2026-10-31T18:30:00Z"))
                .andExpect(jsonPath("$.recentPayments[1].method").value("BKASH"))
                // 8 notices exist, the admin sees all audiences, 5 at most, pinned first
                .andExpect(jsonPath("$.notices.length()").value(5))
                .andExpect(jsonPath("$.notices[0].title").value("Pinned"));
    }

    @Test
    void teacherDashboard_listsOwnClassesOfThisYear_withCountsAndTakenToday() throws Exception {
        School school = seedSchool();
        String token = login("t-1001", "TeacherPass#1");

        getTeacher(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teacherName").value("Ayesha Rahman"))
                .andExpect(jsonPath("$.teachingLoad.classTeacherOf.length()").value(2))
                .andExpect(jsonPath("$.teachingLoad.subjects[0].subjectCode").value("MATH"))
                // class teacher of 9-A, math in 9-B; the 2025 class is not today's work
                .andExpect(jsonPath("$.classesToday.length()").value(2))
                .andExpect(jsonPath("$.classesToday[0].classId").value(school.class9A.getId()))
                .andExpect(jsonPath("$.classesToday[0].className").value("Class 9 - A"))
                .andExpect(jsonPath("$.classesToday[0].students").value(2))
                .andExpect(jsonPath("$.classesToday[0].attendanceTaken").value(true))
                .andExpect(jsonPath("$.classesToday[1].className").value("Class 9 - B"))
                .andExpect(jsonPath("$.classesToday[1].students").value(1))
                .andExpect(jsonPath("$.classesToday[1].attendanceTaken").value(false))
                // the STUDENTS-only notice is hidden from teachers: 7 visible, 5 shown
                .andExpect(jsonPath("$.notices.length()").value(5))
                .andExpect(jsonPath("$.notices[?(@.audience == 'STUDENTS')]").isEmpty());
    }

    @Test
    void teacherWithoutClasses_getsEmptyList() throws Exception {
        seedSchool();
        String token = login("t-1002", "TeacherPass#1");

        getTeacher(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teacherName").value("Karim Rahman"))
                .andExpect(jsonPath("$.classesToday.length()").value(0));
    }

    @Test
    void adminDashboard_runsAFixedNumberOfQueries_howeverManyRows() throws Exception {
        School school = seedSchool();
        long before = countAdminQueries();

        // More students, attendance, payments and marks: the number of SQL statements must not grow.
        Exam exam = examRepository.findAll().stream().filter(e -> e.getName().equals("Half Yearly")).findFirst()
                .orElseThrow();
        Subject math = subjectRepository.findAll().stream().filter(s -> s.getCode().equals("MATH")).findFirst()
                .orElseThrow();
        for (int i = 10; i < 16; i++) {
            Student extra = student("ADM-2026-0" + i, "Extra" + i, "Khan", Gender.MALE, school.class9A, i,
                    StudentStatus.ACTIVE);
            attendanceRecordRepository.save(new AttendanceRecord(extra, school.class9A, TODAY,
                    AttendanceStatus.PRESENT, null));
            markRepository.save(new Mark(exam, extra, math, new BigDecimal("55"), false));
            FeeInvoice invoice = new FeeInvoice("INV-202611-X0000" + i, extra, "Tuition", new BigDecimal("100"),
                    BigDecimal.ZERO, LocalDate.of(2026, 12, 31));
            invoice.applyPayment(new BigDecimal("100"));
            invoice = feeInvoiceRepository.save(invoice);
            paymentRepository.save(new Payment(invoice, new BigDecimal("100"), PaymentMethod.CASH, "C-" + i,
                    "extra-" + i, Instant.parse("2026-11-20T04:00:00Z")));
        }
        long after = countAdminQueries();

        assertThat(after).isEqualTo(before);
    }

    private long countAdminQueries() throws Exception {
        entityManager.flush();
        entityManager.clear();
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        try {
            getAdmin(adminToken).andExpect(status().isOk());
            return statistics.getPrepareStatementCount();
        } finally {
            statistics.setStatisticsEnabled(false);
        }
    }

    @Test
    void adminEndpoint_allowsAdminOnly() throws Exception {
        seedSchool();
        String teacherToken = login("t-1001", "TeacherPass#1");
        String studentToken = login("adm-2026-001", "StudentPass#1");

        getAdmin(adminToken).andExpect(status().isOk());
        getAdmin(teacherToken).andExpect(status().isForbidden());
        getAdmin(studentToken).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/dashboard/admin")).andExpect(status().isUnauthorized());
    }

    @Test
    void teacherEndpoint_allowsTeacherOnly() throws Exception {
        seedSchool();
        String teacherToken = login("t-1001", "TeacherPass#1");
        String studentToken = login("adm-2026-001", "StudentPass#1");

        getTeacher(teacherToken).andExpect(status().isOk());
        getTeacher(adminToken).andExpect(status().isForbidden());
        getTeacher(studentToken).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/dashboard/teacher")).andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- data

    private record School(SchoolClass class9A, String aminaInvoiceNo) {
    }

    private School seedSchool() {
        Teacher ayesha = teacher("t-1001", "T-1001", "Ayesha", TeacherStatus.ACTIVE);
        teacher("t-1002", "T-1002", "Karim", TeacherStatus.ACTIVE);
        Teacher rina = teacher("t-1003", "T-1003", "Rina", TeacherStatus.ON_LEAVE);

        AcademicYear old = academicYearRepository.save(
                new AcademicYear("2025", LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)));
        AcademicYear year = new AcademicYear("2026", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        year.setCurrent(true);
        year = academicYearRepository.save(year);

        schoolClassRepository.save(new SchoolClass(old, "Class 8", "A", "201", 40, ayesha));
        SchoolClass class9A = schoolClassRepository.save(new SchoolClass(year, "Class 9", "A", "101", 40, ayesha));
        SchoolClass class9B = schoolClassRepository.save(new SchoolClass(year, "Class 9", "B", "102", 40, rina));
        Subject math = subjectRepository.save(new Subject("MATH", "Mathematics", 100, 33));
        Subject english = subjectRepository.save(new Subject("ENG", "English", 100, 33));
        classSubjectRepository.save(new ClassSubject(class9B, math, ayesha));

        AppUser aminaLogin = appUserRepository.save(new AppUser(
                "adm-2026-001", "amina@school.edu", passwordEncoder.encode("StudentPass#1"), "Amina Islam",
                Role.STUDENT));
        Student amina = student("ADM-2026-001", "Amina", "Islam", Gender.FEMALE, class9A, 1, StudentStatus.ACTIVE);
        amina.setUser(aminaLogin);
        Student babul = student("ADM-2026-002", "Babul", "Hossain", Gender.MALE, class9A, 2, StudentStatus.ACTIVE);
        student("ADM-2026-003", "Chandni", "Akter", Gender.FEMALE, class9A, 3, StudentStatus.TRANSFERRED);
        Student dipu = student("ADM-2026-004", "Dipu", "Das", Gender.MALE, class9B, 1, StudentStatus.ACTIVE);

        // Attendance: today (9-A only), yesterday (both classes), and 20 days ago (outside the trend).
        attendanceRecordRepository.save(new AttendanceRecord(amina, class9A, TODAY, AttendanceStatus.PRESENT, null));
        attendanceRecordRepository.save(new AttendanceRecord(babul, class9A, TODAY, AttendanceStatus.ABSENT, null));
        LocalDate yesterday = TODAY.minusDays(1);
        attendanceRecordRepository.save(new AttendanceRecord(amina, class9A, yesterday, AttendanceStatus.PRESENT, null));
        attendanceRecordRepository.save(new AttendanceRecord(babul, class9A, yesterday, AttendanceStatus.LATE, null));
        attendanceRecordRepository.save(new AttendanceRecord(dipu, class9B, yesterday, AttendanceStatus.EXCUSED, null));
        attendanceRecordRepository.save(new AttendanceRecord(
                amina, class9A, TODAY.minusDays(20), AttendanceStatus.ABSENT, null));

        // Fees: Amina owes 600 of 1000 and is past due; Babul paid 500 in full in September.
        FeeInvoice aminaInvoice = new FeeInvoice("INV-202610-AAAAAA", amina, "Tuition", new BigDecimal("1000"),
                BigDecimal.ZERO, LocalDate.of(2026, 11, 10));
        aminaInvoice.applyPayment(new BigDecimal("400"));
        aminaInvoice = feeInvoiceRepository.save(aminaInvoice);
        FeeInvoice babulInvoice = new FeeInvoice("INV-202609-BBBBBB", babul, "Tuition", new BigDecimal("500"),
                BigDecimal.ZERO, LocalDate.of(2026, 9, 30));
        babulInvoice.applyPayment(new BigDecimal("500"));
        babulInvoice = feeInvoiceRepository.save(babulInvoice);
        paymentRepository.save(new Payment(aminaInvoice, new BigDecimal("400"), PaymentMethod.CASH, "CASH-1",
                "key-1", Instant.parse("2026-10-31T18:30:00Z")));
        paymentRepository.save(new Payment(babulInvoice, new BigDecimal("500"), PaymentMethod.BKASH, "BK-1",
                "key-2", Instant.parse("2026-09-15T06:00:00Z")));

        // Exams: an older published one, the newest published one, and a newer DRAFT that must be ignored.
        Exam firstTerm = new Exam(year, "First Term", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 10));
        firstTerm.publish(Instant.parse("2026-04-01T04:00:00Z"));
        examRepository.save(firstTerm);
        Exam halfYearly = new Exam(year, "Half Yearly", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 10));
        halfYearly.publish(Instant.parse("2026-07-01T04:00:00Z"));
        halfYearly = examRepository.save(halfYearly);
        examRepository.save(new Exam(year, "Final", LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 30)));
        markRepository.save(new Mark(halfYearly, amina, math, new BigDecimal("85"), false));
        markRepository.save(new Mark(halfYearly, amina, english, new BigDecimal("75"), false));
        markRepository.save(new Mark(halfYearly, babul, math, new BigDecimal("20"), false));
        markRepository.save(new Mark(halfYearly, babul, english, new BigDecimal("90"), false));
        markRepository.save(new Mark(halfYearly, dipu, math, new BigDecimal("82"), false));
        markRepository.save(new Mark(halfYearly, dipu, english, new BigDecimal("91"), false));
        markRepository.save(new Mark(firstTerm, dipu, math, new BigDecimal("10"), false));

        // Notices: 5 for everybody, 1 for teachers, 1 for students; one pinned.
        for (int i = 1; i <= 5; i++) {
            noticeRepository.save(new Notice("All " + i, "Body", NoticeAudience.ALL, false,
                    Instant.parse("2026-11-0" + i + "T04:00:00Z")));
        }
        noticeRepository.save(new Notice("Teachers", "Body", NoticeAudience.TEACHERS, false,
                Instant.parse("2026-11-20T04:00:00Z")));
        noticeRepository.save(new Notice("Students", "Body", NoticeAudience.STUDENTS, false,
                Instant.parse("2026-11-21T04:00:00Z")));
        noticeRepository.save(new Notice("Pinned", "Body", NoticeAudience.ALL, true,
                Instant.parse("2026-10-01T04:00:00Z")));
        noticeRepository.flush();
        return new School(class9A, aminaInvoice.getInvoiceNo());
    }

    private Teacher teacher(String username, String employeeNo, String firstName, TeacherStatus status) {
        AppUser user = appUserRepository.save(new AppUser(
                username, username + "@school.edu", passwordEncoder.encode("TeacherPass#1"),
                firstName + " Rahman", Role.TEACHER));
        Teacher teacher = new Teacher(user, employeeNo, firstName, "Rahman", username + "@school.edu",
                "01711111111", "Teacher", LocalDate.of(2024, 1, 1));
        teacher.setStatus(status);
        return teacherRepository.save(teacher);
    }

    private Student student(String admissionNo, String firstName, String lastName, Gender gender,
                            SchoolClass schoolClass, int roll, StudentStatus status) {
        Student student = new Student(admissionNo);
        student.setFirstName(firstName);
        student.setLastName(lastName);
        student.setGender(gender);
        student.setDateOfBirth(LocalDate.of(2011, 5, 10));
        student.setGuardianName("Parent " + firstName);
        student.setGuardianPhone("0170000000" + roll);
        student.setGuardianRelation("Father");
        student.setAdmissionDate(LocalDate.of(2026, 1, 5));
        student.setSchoolClass(schoolClass);
        student.setRollNo(roll);
        student.setStatus(status);
        return studentRepository.save(student);
    }

    private ResultActions getAdmin(String token) throws Exception {
        return mockMvc.perform(get("/api/v1/dashboard/admin").header("Authorization", "Bearer " + token));
    }

    private ResultActions getTeacher(String token) throws Exception {
        return mockMvc.perform(get("/api/v1/dashboard/teacher").header("Authorization", "Bearer " + token));
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }
}
