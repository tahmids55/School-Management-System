package com.school.demo;

import com.school.CommittedIntegrationTestBase;
import com.school.academic.AcademicYearRepository;
import com.school.academic.ClassSubjectRepository;
import com.school.academic.SchoolClass;
import com.school.academic.SchoolClassRepository;
import com.school.academic.Subject;
import com.school.academic.SubjectRepository;
import com.school.attendance.AttendanceRecordRepository;
import com.school.exam.Exam;
import com.school.exam.ExamRepository;
import com.school.exam.MarkRepository;
import com.school.fee.FeeInvoiceRepository;
import com.school.fee.PaymentRepository;
import com.school.notice.NoticeRepository;
import com.school.student.Student;
import com.school.student.StudentRepository;
import com.school.teacher.TeacherRepository;
import com.school.user.AppUser;
import com.school.user.AppUserRepository;
import com.school.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F32 on the real database. The seeder is built here with a clock fixed at 10:00 on Thursday 26 Nov 2026
 * in Dhaka (the app's own seeder is switched off in tests) and runs in one committed transaction,
 * like at startup. Data is committed, so the demo flow can see notifications (they are sent after commit).
 */
class DemoDataSeederIntegrationTest extends CommittedIntegrationTestBase {

    private static final Instant NOW = Instant.parse("2026-11-26T04:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneId.of("Asia/Dhaka"));
    private static final String ADMIN_PASSWORD = "AdminPass#1";
    private static final String DEMO_PASSWORD = "DemoPass#1";

    @Autowired private AppUserRepository appUserRepository;
    @Autowired private AcademicYearRepository academicYearRepository;
    @Autowired private SubjectRepository subjectRepository;
    @Autowired private TeacherRepository teacherRepository;
    @Autowired private SchoolClassRepository schoolClassRepository;
    @Autowired private ClassSubjectRepository classSubjectRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private AttendanceRecordRepository attendanceRecordRepository;
    @Autowired private ExamRepository examRepository;
    @Autowired private MarkRepository markRepository;
    @Autowired private FeeInvoiceRepository feeInvoiceRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private NoticeRepository noticeRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TransactionTemplate transactionTemplate;

    @Test
    void freshDatabase_getsTheDemoSchool() {
        assertThat(seed()).isTrue();

        // Year, subjects
        assertThat(sql("select name || ' ' || start_date || ' ' || end_date || ' ' || current from academic_years"))
                .containsExactly("2026 2026-01-01 2026-12-31 true");
        assertThat(sql("select code || ' ' || full_marks || '/' || pass_marks from subjects order by code"))
                .containsExactly("BAN101 100/33", "BGS101 100/33", "ENG101 100/33", "ICT101 50/17",
                        "MAT101 100/33", "SCI101 100/33");

        // Teachers with logins; nobody must change the password first
        assertThat(sql("""
                select t.employee_no || ' ' || t.first_name || ' ' || t.last_name || ' ' || t.designation || ' '
                       || u.username || ' ' || t.email || ' ' || u.must_change_password
                from teachers t join users u on u.id = t.user_id order by t.employee_no"""))
                .containsExactly(
                        "T-1001 Ayesha Rahman Senior Teacher teacher ayesha.rahman@school.edu false",
                        "T-1002 Kamal Hossain Assistant Teacher t-1002 kamal.hossain@school.edu false",
                        "T-1003 Nusrat Jahan Assistant Teacher t-1003 nusrat.jahan@school.edu false",
                        "T-1004 Imran Khan Assistant Teacher t-1004 imran.khan@school.edu false",
                        "T-1005 Farhana Akter Senior Teacher t-1005 farhana.akter@school.edu false",
                        "T-1006 Rafiq Islam Assistant Teacher t-1006 rafiq.islam@school.edu false");

        // Classes with rooms and class teachers; Ayesha teaches Mathematics everywhere; 6 subjects each
        assertThat(sql("""
                select c.name || '-' || c.section || ' ' || c.room || ' ' || c.capacity || ' ' || t.employee_no
                from school_classes c join teachers t on t.id = c.class_teacher_id order by c.name"""))
                .containsExactly("Class 6-A R-101 40 T-1001", "Class 7-A R-102 40 T-1003", "Class 8-A R-103 40 T-1004");
        assertThat(sql("""
                select c.name || ' ' || t.employee_no from class_subjects cs
                join school_classes c on c.id = cs.school_class_id join subjects s on s.id = cs.subject_id
                join teachers t on t.id = cs.teacher_id where s.code = 'MAT101' order by c.name"""))
                .containsExactly("Class 6 T-1001", "Class 7 T-1001", "Class 8 T-1001");
        assertThat(classSubjectRepository.count()).isEqualTo(18);
        // The other five teachers share the other 15 sheets, three each
        assertThat(sql("""
                select count(*) from class_subjects cs join teachers t on t.id = cs.teacher_id
                where t.employee_no <> 'T-1001' group by t.employee_no"""))
                .containsExactly("3", "3", "3", "3", "3");

        // Students: 12 per class, numbered across the school, father as guardian, ages by class
        assertThat(studentRepository.count()).isEqualTo(36);
        assertThat(sql("select count(*) from students group by school_class_id")).containsOnly("12");
        assertThat(sql("select min(admission_no) || ' ' || max(admission_no) from students"))
                .containsExactly("ADM-2026-001 ADM-2026-036");
        assertThat(sql("select count(*) from students where guardian_relation = 'Father' and address like '%Lake Road%'"))
                .containsExactly("36");
        assertThat(sql("""
                select c.name || ' ' || min(extract(year from s.date_of_birth)) || ' '
                       || max(extract(year from s.date_of_birth))
                from students s join school_classes c on c.id = s.school_class_id group by c.name order by c.name"""))
                .containsExactly("Class 6 2014 2014", "Class 7 2013 2013", "Class 8 2012 2012");
        assertThat(sql("""
                select s.first_name || ' ' || s.last_name || ' ' || c.name || ' roll ' || s.roll_no || ' ' || u.username
                       || ' ' || u.email || ' ' || u.role || ' ' || u.must_change_password
                from students s join users u on u.id = s.user_id join school_classes c on c.id = s.school_class_id"""))
                .containsExactly("Arif Hossain Class 6 roll 1 student student@school.edu STUDENT false");

        // Attendance: 30 school days up to today, never Friday (5) or Saturday (6), every student every day
        assertThat(attendanceRecordRepository.count()).isEqualTo(30 * 36);
        assertThat(sql("""
                select count(distinct attendance_date) || ' ' || min(attendance_date) || ' ' || max(attendance_date)
                from attendance_records"""))
                .containsExactly("30 2026-10-18 2026-11-26");
        assertThat(sql("select count(*) from attendance_records where extract(dow from attendance_date) in (5, 6)"))
                .containsExactly("0");
        // Everybody but roll 5 attends well; roll 5 is below the 75 % warning level in every class
        double normal = attended("s.roll_no <> 5");
        assertThat(normal).isBetween(88.0, 98.0);
        for (String percent : sql("""
                select round(100.0 * sum(case when a.status in ('PRESENT', 'LATE') then 1 else 0 end)
                       / sum(case when a.status <> 'EXCUSED' then 1 else 0 end), 1)
                from attendance_records a join students s on s.id = a.student_id
                where s.roll_no = 5 group by s.id""")) {
            assertThat(Double.parseDouble(percent)).isLessThan(75.0);
        }

        // Exams: Half-Yearly published on 2 July, Annual still a draft
        assertThat(sql("""
                select name || ' ' || start_date || ' ' || end_date || ' ' || status || ' '
                       || coalesce(to_char(published_at at time zone 'Asia/Dhaka', 'YYYY-MM-DD HH24:MI'), '-')
                from exams order by start_date"""))
                .containsExactly(
                        "Half-Yearly Examination 2026-06-08 2026-06-19 PUBLISHED 2026-07-02 10:00",
                        "Annual Examination 2026-11-25 2026-12-10 DRAFT -");
        // Marks: every sheet of the Half-Yearly; every Annual sheet except Class 6-A Mathematics
        assertThat(sql("""
                select e.name || ' ' || count(*) from marks m join exams e on e.id = m.exam_id
                group by e.name order by e.name"""))
                .containsExactly("Annual Examination 204", "Half-Yearly Examination 216");
        assertThat(sql("""
                select count(*) from marks m join exams e on e.id = m.exam_id join subjects s on s.id = m.subject_id
                join students st on st.id = m.student_id join school_classes c on c.id = st.school_class_id
                where e.name = 'Annual Examination' and s.code = 'MAT101' and c.name = 'Class 6'"""))
                .containsExactly("0");
        assertThat(Integer.parseInt(sql("select count(*) from marks where absent").get(0))).isBetween(1, 20);

        // Fees: session + 6 months of tuition per student
        assertThat(feeInvoiceRepository.count()).isEqualTo(36 * 7);
        assertThat(sql("select status || ' ' || count(*) from fee_invoices where title = 'Session fee 2026' group by status"))
                .containsExactly("PAID 36");
        assertThat(sql("select distinct method from payments p join fee_invoices i on i.id = p.invoice_id "
                + "where i.title = 'Session fee 2026'")).containsExactly("CASH");
        assertThat(sql("select distinct status from fee_invoices where due_date > '2026-11-26'"))
                .containsExactly("UNPAID");
        assertThat(sql("select distinct amount_paid from fee_invoices where status = 'PARTIALLY_PAID'"))
                .containsExactly("500.00");
        assertThat(paidShare("2026-07-10")).isGreaterThan(0.7);
        assertThat(paidShare("2026-11-10")).isLessThan(0.7);
        // Never a payment in the future, and the invoices agree with their payments
        assertThat(sql("select count(*) from payments where paid_at > '2026-11-26T04:00:00Z'")).containsExactly("0");
        assertThat(sql("select ((select sum(amount_paid) from fee_invoices) = (select sum(amount) from payments))::text"))
                .containsExactly("true");

        // Notices
        assertThat(sql("select title || ' ' || audience || ' ' || pinned from notices order by published_at"))
                .containsExactly(
                        "Welcome to the new academic year ALL true",
                        "Annual Examination schedule STUDENTS false",
                        "Staff meeting on Sunday TEACHERS false",
                        "Victory Day holiday ALL false");
    }

    @Test
    void secondStart_addsNothing() {
        assertThat(seed()).isTrue();
        List<Long> before = counts();

        assertThat(seed()).isFalse();

        assertThat(counts()).isEqualTo(before);
    }

    @Test
    void twoFreshDatabases_getTheSameSchool() {
        seed();
        String first = fingerprint();

        truncateAll();
        seed();
        String second = fingerprint();

        assertThat(second).isEqualTo(first);
    }

    @Test
    void demoFlow_enter6AMaths_publish_reportCard_andStudentNotification() throws Exception {
        appUserRepository.save(new AppUser("admin", "admin@school.edu", passwordEncoder.encode(ADMIN_PASSWORD),
                "Nasima Akter", Role.ADMIN));
        seed();
        String admin = login("admin", ADMIN_PASSWORD);
        String ayesha = login("teacher", DEMO_PASSWORD);
        String kamal = login("t-1002", DEMO_PASSWORD);
        String student = login("student", DEMO_PASSWORD);
        Exam annual = examRepository.findAll().stream()
                .filter(e -> e.getName().equals(DemoDataSeeder.ANNUAL)).findFirst().orElseThrow();
        SchoolClass class6 = schoolClassRepository.findAll().stream()
                .filter(c -> c.getName().equals("Class 6")).findFirst().orElseThrow();
        Subject maths = subjectRepository.findAll().stream()
                .filter(s -> s.getCode().equals("MAT101")).findFirst().orElseThrow();

        // 1. Publishing fails while the one demo sheet is missing.
        mockMvc.perform(post("/api/v1/exams/{id}/publish", annual.getId()).header("Authorization", bearer(admin)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail")
                        .value("Cannot publish results: missing marks for Class 6 - A / MAT101"));

        // 2. Ayesha (she teaches 6-A Mathematics) enters the sheet; Kamal may not.
        List<Student> roster = studentRepository.findActiveRoster(class6.getId());
        List<Map<String, Object>> entries = new ArrayList<>();
        for (int i = 0; i < roster.size(); i++) {
            entries.add(Map.of("studentId", roster.get(i).getId(), "marksObtained", new BigDecimal(60 + i),
                    "absent", false));
        }
        String sheet = objectMapper.writeValueAsString(Map.of(
                "classId", class6.getId(), "subjectId", maths.getId(), "entries", entries));
        mockMvc.perform(put("/api/v1/exams/{id}/marks", annual.getId()).header("Authorization", bearer(kamal))
                        .contentType(MediaType.APPLICATION_JSON).content(sheet))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/exams/{id}/marks", annual.getId()).header("Authorization", bearer(ayesha))
                        .contentType(MediaType.APPLICATION_JSON).content(sheet))
                .andExpect(status().isOk());

        // 3. Now publishing succeeds.
        mockMvc.perform(post("/api/v1/exams/{id}/publish", annual.getId()).header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        // 4. The demo student downloads the own report card, but not a classmate's.
        Long arif = roster.get(0).getId();
        mockMvc.perform(get("/api/v1/exams/{id}/students/{studentId}/report-card", annual.getId(), arif)
                        .header("Authorization", bearer(student)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PDF));
        mockMvc.perform(get("/api/v1/exams/{id}/students/{studentId}/report-card", annual.getId(), roster.get(1).getId())
                        .header("Authorization", bearer(student)))
                .andExpect(status().isForbidden());

        // 5. ... and got a notification about it.
        mockMvc.perform(get("/api/v1/notifications").header("Authorization", bearer(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].title").value(hasItem("Results published")))
                .andExpect(jsonPath("$.content[*].message")
                        .value(hasItem("Results of Annual Examination are now available.")));
    }

    // ---------------------------------------------------------------- helpers

    /** Like at startup: seeding enabled, both passwords set, one transaction that commits. */
    private boolean seed() {
        DemoDataSeeder seeder = new DemoDataSeeder(true, ADMIN_PASSWORD, DEMO_PASSWORD, appUserRepository,
                academicYearRepository, subjectRepository, teacherRepository, schoolClassRepository,
                classSubjectRepository, studentRepository, attendanceRecordRepository, examRepository,
                markRepository, feeInvoiceRepository, paymentRepository, noticeRepository, passwordEncoder, CLOCK);
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> seeder.seed()));
    }

    private List<String> sql(String query) {
        return jdbcTemplate.queryForList(query, String.class);
    }

    private double attended(String where) {
        return Double.parseDouble(sql("""
                select round(100.0 * sum(case when a.status in ('PRESENT', 'LATE') then 1 else 0 end)
                       / sum(case when a.status <> 'EXCUSED' then 1 else 0 end), 1)
                from attendance_records a join students s on s.id = a.student_id where\s""" + where).get(0));
    }

    private double paidShare(String dueDate) {
        return Double.parseDouble(sql("select avg(case when status = 'PAID' then 1.0 else 0.0 end) "
                + "from fee_invoices where due_date = '" + dueDate + "'").get(0));
    }

    private List<Long> counts() {
        return List.of(academicYearRepository.count(), subjectRepository.count(), teacherRepository.count(),
                appUserRepository.count(), schoolClassRepository.count(), classSubjectRepository.count(),
                studentRepository.count(), attendanceRecordRepository.count(), examRepository.count(),
                markRepository.count(), feeInvoiceRepository.count(), paymentRepository.count(),
                noticeRepository.count());
    }

    /**
     * One hash over the content of every seeded table (ids included, audit timestamps and bcrypt hashes left out:
     * those come from the wall clock and a random salt, not from the demo plan).
     */
    private String fingerprint() {
        return sql("""
                select md5(string_agg(line, '|' order by line)) from (
                  select 'y' || name || start_date || end_date || current as line from academic_years
                  union all select 's' || id || code || name || full_marks || pass_marks from subjects
                  union all select 'u' || id || username || email || full_name || role from users
                  union all select 't' || id || user_id || employee_no || first_name || last_name || email || phone
                                   || designation || joining_date from teachers
                  union all select 'c' || id || name || section || room || capacity || class_teacher_id from school_classes
                  union all select 'cs' || school_class_id || '/' || subject_id || '/' || teacher_id from class_subjects
                  union all select 'st' || id || admission_no || first_name || last_name || gender || date_of_birth
                                   || coalesce(email, '') || address || guardian_name || guardian_phone
                                   || school_class_id || '/' || roll_no || coalesce(user_id::text, '') from students
                  union all select 'a' || student_id || '/' || attendance_date || status || coalesce(remarks, '')
                                   from attendance_records
                  union all select 'e' || id || name || start_date || end_date || status
                                   || coalesce(published_at::text, '') from exams
                  union all select 'm' || exam_id || '/' || student_id || '/' || subject_id || '/'
                                   || coalesce(marks_obtained::text, 'absent') from marks
                  union all select 'i' || invoice_no || student_id || title || amount || amount_paid || status || due_date
                                   from fee_invoices
                  union all select 'p' || invoice_id || '/' || amount || method || transaction_ref || paid_at from payments
                  union all select 'n' || title || body || audience || pinned || published_at from notices
                ) everything""").get(0);
    }

    private void truncateAll() {
        List<String> tables = sql("select tablename from pg_tables where schemaname = 'public' "
                + "and tablename <> 'flyway_schema_history'");
        jdbcTemplate.execute("TRUNCATE TABLE " + String.join(", ", tables) + " RESTART IDENTITY CASCADE");
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
