package com.school.demo;

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
import com.school.demo.DemoPlan.PaymentState;
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
import com.school.teacher.Teacher;
import com.school.teacher.TeacherRepository;
import com.school.user.AppUser;
import com.school.user.AppUserRepository;
import com.school.user.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * F32: builds the same demo school on every fresh database, for the live demo and for manual testing.
 * <ul>
 *   <li>Runs after {@link com.school.user.AdminBootstrap} (Order 1), only when {@code app.seed.enabled}.</li>
 *   <li>Only on an EMPTY school: no academic year, no student and no teacher. A second start adds nothing.</li>
 *   <li>Deterministic: {@code new Random(42)} and "today" from the injected Clock, so two fresh databases
 *       seeded on the same day get exactly the same rows.</li>
 *   <li>One transaction: if any row fails, nothing is left half-seeded. Each table is saved with one saveAll.</li>
 * </ul>
 * Annual Examination marks are seeded for every sheet EXCEPT Class 6-A Mathematics: the demo enters that
 * one sheet live, then publishing succeeds (it fails while a sheet is missing).
 */
@Component
@Order(2)
public class DemoDataSeeder implements ApplicationRunner {

    public static final String MISSING_PASSWORDS = "Set ADMIN_PASSWORD and DEMO_PASSWORD to seed demo data";

    static final long RANDOM_SEED = 42L;
    static final int STUDENTS_PER_CLASS = 12;
    /** Roll 5 of every class attends badly (about 62 %), so the low-attendance warning can be shown. */
    static final int WEAK_ROLL = 5;
    static final String HALF_YEARLY = "Half-Yearly Examination";
    static final String ANNUAL = "Annual Examination";
    static final String DEMO_SHEET_CLASS = "Class 6";
    static final String DEMO_SHEET_SUBJECT = "MAT101";

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final BigDecimal SESSION_FEE = new BigDecimal("5000");
    private static final BigDecimal TUITION_FEE = new BigDecimal("1500");
    private static final BigDecimal PART_PAYMENT = new BigDecimal("500");
    private static final DateTimeFormatter INVOICE_MONTH = DateTimeFormatter.ofPattern("yyyyMM");
    private static final PaymentMethod[] TUITION_METHODS = PaymentMethod.values();

    /** code, name, full marks, pass marks. Mathematics is index 2. */
    private static final List<SubjectSpec> SUBJECTS = List.of(
            new SubjectSpec("BAN101", "Bangla", 100, 33),
            new SubjectSpec("ENG101", "English", 100, 33),
            new SubjectSpec("MAT101", "Mathematics", 100, 33),
            new SubjectSpec("SCI101", "General Science", 100, 33),
            new SubjectSpec("BGS101", "Bangladesh & Global Studies", 100, 33),
            new SubjectSpec("ICT101", "Information & Communication Technology", 50, 17));

    /** T-1001 is index 0 and logs in as "teacher"; the others log in as t-1002 ... t-1006. */
    private static final List<TeacherSpec> TEACHERS = List.of(
            new TeacherSpec("T-1001", "Ayesha", "Rahman", "Senior Teacher", LocalDate.of(2012, 1, 15)),
            new TeacherSpec("T-1002", "Kamal", "Hossain", "Assistant Teacher", LocalDate.of(2016, 3, 1)),
            new TeacherSpec("T-1003", "Nusrat", "Jahan", "Assistant Teacher", LocalDate.of(2017, 7, 1)),
            new TeacherSpec("T-1004", "Imran", "Khan", "Assistant Teacher", LocalDate.of(2018, 1, 10)),
            new TeacherSpec("T-1005", "Farhana", "Akter", "Senior Teacher", LocalDate.of(2013, 6, 1)),
            new TeacherSpec("T-1006", "Rafiq", "Islam", "Assistant Teacher", LocalDate.of(2020, 2, 1)));

    /** Class 6, 7, 8 (section A) with the index of their class teacher: T-1001, T-1003, T-1004. */
    private static final int[] CLASS_NUMBERS = {6, 7, 8};
    private static final int[] CLASS_TEACHERS = {0, 2, 3};

    /** 12 students per class, in roll order: first name, last name, gender, father's first name. */
    private static final List<List<StudentSpec>> STUDENTS = List.of(
            List.of(
                    new StudentSpec("Arif", "Hossain", Gender.MALE, "Jamal"),
                    new StudentSpec("Sadia", "Islam", Gender.FEMALE, "Rafiqul"),
                    new StudentSpec("Tahmid", "Rahman", Gender.MALE, "Habibur"),
                    new StudentSpec("Mim", "Akter", Gender.FEMALE, "Abdul"),
                    new StudentSpec("Rakib", "Hasan", Gender.MALE, "Mizanur"),
                    new StudentSpec("Jannatul", "Ferdous", Gender.FEMALE, "Shahidul"),
                    new StudentSpec("Nayeem", "Khan", Gender.MALE, "Anwar"),
                    new StudentSpec("Tasnim", "Chowdhury", Gender.FEMALE, "Mahbub"),
                    new StudentSpec("Fahim", "Uddin", Gender.MALE, "Nasir"),
                    new StudentSpec("Lamia", "Sultana", Gender.FEMALE, "Kamrul"),
                    new StudentSpec("Shakib", "Mia", Gender.MALE, "Delwar"),
                    new StudentSpec("Raisa", "Alam", Gender.FEMALE, "Shafiqul")),
            List.of(
                    new StudentSpec("Sabbir", "Ahmed", Gender.MALE, "Mostafa"),
                    new StudentSpec("Farzana", "Yasmin", Gender.FEMALE, "Golam"),
                    new StudentSpec("Imtiaz", "Karim", Gender.MALE, "Rezaul"),
                    new StudentSpec("Nabila", "Haque", Gender.FEMALE, "Aminul"),
                    new StudentSpec("Junaid", "Sarker", Gender.MALE, "Monir"),
                    new StudentSpec("Tanjila", "Begum", Gender.FEMALE, "Abul"),
                    new StudentSpec("Mahin", "Talukder", Gender.MALE, "Faruk"),
                    new StudentSpec("Sumaiya", "Rahman", Gender.FEMALE, "Lutfur"),
                    new StudentSpec("Asif", "Iqbal", Gender.MALE, "Zahid"),
                    new StudentSpec("Ritu", "Das", Gender.FEMALE, "Gopal"),
                    new StudentSpec("Zubair", "Hossain", Gender.MALE, "Alamgir"),
                    new StudentSpec("Puja", "Saha", Gender.FEMALE, "Nikhil")),
            List.of(
                    new StudentSpec("Ridwan", "Kabir", Gender.MALE, "Humayun"),
                    new StudentSpec("Afsana", "Khatun", Gender.FEMALE, "Abdur"),
                    new StudentSpec("Shahriar", "Nafis", Gender.MALE, "Nurul"),
                    new StudentSpec("Maliha", "Tabassum", Gender.FEMALE, "Jahangir"),
                    new StudentSpec("Tanvir", "Islam", Gender.MALE, "Saiful"),
                    new StudentSpec("Nadia", "Parvin", Gender.FEMALE, "Mosharraf"),
                    new StudentSpec("Rifat", "Mahmud", Gender.MALE, "Obaidul"),
                    new StudentSpec("Tahsin", "Noor", Gender.FEMALE, "Ziaul"),
                    new StudentSpec("Sajid", "Hasan", Gender.MALE, "Belal"),
                    new StudentSpec("Anika", "Tasnim", Gender.FEMALE, "Harun"),
                    new StudentSpec("Ehsan", "Ali", Gender.MALE, "Siddiq"),
                    new StudentSpec("Mehjabin", "Chowdhury", Gender.FEMALE, "Ashraf")));

    private final boolean enabled;
    private final String adminPassword;
    private final String demoPassword;
    private final AppUserRepository appUserRepository;
    private final AcademicYearRepository academicYearRepository;
    private final SubjectRepository subjectRepository;
    private final TeacherRepository teacherRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final ClassSubjectRepository classSubjectRepository;
    private final StudentRepository studentRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final ExamRepository examRepository;
    private final MarkRepository markRepository;
    private final FeeInvoiceRepository feeInvoiceRepository;
    private final PaymentRepository paymentRepository;
    private final NoticeRepository noticeRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public DemoDataSeeder(
            @Value("${app.seed.enabled:false}") boolean enabled,
            @Value("${app.seed.admin-password:}") String adminPassword,
            @Value("${app.seed.demo-password:}") String demoPassword,
            AppUserRepository appUserRepository,
            AcademicYearRepository academicYearRepository,
            SubjectRepository subjectRepository,
            TeacherRepository teacherRepository,
            SchoolClassRepository schoolClassRepository,
            ClassSubjectRepository classSubjectRepository,
            StudentRepository studentRepository,
            AttendanceRecordRepository attendanceRecordRepository,
            ExamRepository examRepository,
            MarkRepository markRepository,
            FeeInvoiceRepository feeInvoiceRepository,
            PaymentRepository paymentRepository,
            NoticeRepository noticeRepository,
            PasswordEncoder passwordEncoder,
            Clock clock
    ) {
        this.enabled = enabled;
        this.adminPassword = adminPassword;
        this.demoPassword = demoPassword;
        this.appUserRepository = appUserRepository;
        this.academicYearRepository = academicYearRepository;
        this.subjectRepository = subjectRepository;
        this.teacherRepository = teacherRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.classSubjectRepository = classSubjectRepository;
        this.studentRepository = studentRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.examRepository = examRepository;
        this.markRepository = markRepository;
        this.feeInvoiceRepository = feeInvoiceRepository;
        this.paymentRepository = paymentRepository;
        this.noticeRepository = noticeRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (enabled) {
            seed();
        }
    }

    /**
     * Seeds the demo school when the database has no school yet. Returns true when it seeded.
     * Called by {@link #run} inside its transaction.
     */
    public boolean seed() {
        if (academicYearRepository.count() > 0 || studentRepository.count() > 0 || teacherRepository.count() > 0) {
            log.info("Demo data skipped: the database already has a school");
            return false;
        }
        if (isBlank(adminPassword) || isBlank(demoPassword)) {
            throw new IllegalStateException(MISSING_PASSWORDS);
        }

        Random random = new Random(RANDOM_SEED);
        LocalDate today = LocalDate.now(clock);
        // One hash for every demo login: they all share DEMO_PASSWORD, and bcrypt is slow on purpose.
        String passwordHash = passwordEncoder.encode(demoPassword);

        AcademicYear year = seedYear(today.getYear());
        List<Subject> subjects = subjectRepository.saveAll(SUBJECTS.stream()
                .map(s -> new Subject(s.code(), s.name(), s.fullMarks(), s.passMarks()))
                .toList());
        List<Teacher> teachers = seedTeachers(passwordHash);
        List<SchoolClass> classes = seedClasses(year, teachers);
        seedAssignments(classes, subjects, teachers);
        List<List<Student>> rosters = seedStudents(classes, passwordHash, random, today);
        int attendance = seedAttendance(classes, rosters, random, today);
        int marks = seedExams(year, classes, rosters, subjects, random);
        int invoices = seedFees(rosters, random, today);
        seedNotices(today);

        log.info("Demo data seeded: {} classes, {} teachers, {} students, {} attendance records, {} marks, {} invoices",
                classes.size(), teachers.size(), classes.size() * STUDENTS_PER_CLASS, attendance, marks, invoices);
        return true;
    }

    // ---------------------------------------------------------------- academic

    /** This calendar year, 1 Jan to 31 Dec, and the current one. */
    private AcademicYear seedYear(int year) {
        AcademicYear academicYear = new AcademicYear(
                String.valueOf(year), LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
        academicYear.setCurrent(true);
        return academicYearRepository.save(academicYear);
    }

    /** Six teachers with logins (teacher, t-1002 ... t-1006); nobody has to change the password first. */
    private List<Teacher> seedTeachers(String passwordHash) {
        List<AppUser> users = new ArrayList<>();
        for (int i = 0; i < TEACHERS.size(); i++) {
            TeacherSpec t = TEACHERS.get(i);
            String username = i == 0 ? "teacher" : t.employeeNo().toLowerCase(Locale.ROOT);
            users.add(new AppUser(username, t.email(), passwordHash, t.firstName() + " " + t.lastName(), Role.TEACHER));
        }
        users = appUserRepository.saveAll(users);

        List<Teacher> teachers = new ArrayList<>();
        for (int i = 0; i < TEACHERS.size(); i++) {
            TeacherSpec t = TEACHERS.get(i);
            teachers.add(new Teacher(users.get(i), t.employeeNo(), t.firstName(), t.lastName(), t.email(),
                    "0181100100" + (i + 1), t.designation(), t.joiningDate()));
        }
        return teacherRepository.saveAll(teachers);
    }

    /** Class 6-A, 7-A, 8-A: capacity 40, rooms R-101 to R-103. */
    private List<SchoolClass> seedClasses(AcademicYear year, List<Teacher> teachers) {
        List<SchoolClass> classes = new ArrayList<>();
        for (int i = 0; i < CLASS_NUMBERS.length; i++) {
            classes.add(new SchoolClass(year, "Class " + CLASS_NUMBERS[i], "A", "R-10" + (i + 1), 40,
                    teachers.get(CLASS_TEACHERS[i])));
        }
        return schoolClassRepository.saveAll(classes);
    }

    /**
     * Every class takes all six subjects. Ayesha (T-1001) teaches Mathematics everywhere; the other five
     * subjects rotate over T-1002 ... T-1006, so each of them teaches three sheets.
     */
    private void seedAssignments(List<SchoolClass> classes, List<Subject> subjects, List<Teacher> teachers) {
        List<ClassSubject> assignments = new ArrayList<>();
        for (int c = 0; c < classes.size(); c++) {
            int other = 0;
            for (Subject subject : subjects) {
                Teacher teacher = subject.getCode().equals("MAT101")
                        ? teachers.get(0)
                        : teachers.get(1 + (other++ + c) % 5);
                assignments.add(new ClassSubject(classes.get(c), subject, teacher));
            }
        }
        classSubjectRepository.saveAll(assignments);
    }

    // ---------------------------------------------------------------- students

    /**
     * 12 students per class, ADM-{year}-001 upwards, the father as guardian, Lake Road addresses,
     * born so that a Class 6 student turns 12 this year (Class 7: 13, Class 8: 14).
     * Class 6-A roll 1 logs in as "student".
     */
    private List<List<Student>> seedStudents(List<SchoolClass> classes, String passwordHash, Random random,
                                             LocalDate today) {
        int year = today.getYear();
        LocalDate admissionDate = min(LocalDate.of(year, 1, 2), today);
        StudentSpec first = STUDENTS.get(0).get(0);
        AppUser login = appUserRepository.save(new AppUser("student", "student@school.edu", passwordHash,
                first.firstName() + " " + first.lastName(), Role.STUDENT));

        List<Student> all = new ArrayList<>();
        int number = 0;
        for (int c = 0; c < classes.size(); c++) {
            for (int r = 0; r < STUDENTS_PER_CLASS; r++) {
                number++;
                StudentSpec spec = STUDENTS.get(c).get(r);
                Student student = new Student(String.format("ADM-%d-%03d", year, number));
                student.setFirstName(spec.firstName());
                student.setLastName(spec.lastName());
                student.setGender(spec.gender());
                student.setDateOfBirth(LocalDate.of(year - CLASS_NUMBERS[c] - 6,
                        1 + random.nextInt(12), 1 + random.nextInt(28)));
                student.setAddress("House " + (10 + number) + ", Lake Road, Dhanmondi, Dhaka");
                student.setGuardianName(spec.fatherName() + " " + spec.lastName());
                student.setGuardianRelation("Father");
                student.setGuardianPhone(String.format("01712%06d", number));
                student.setSchoolClass(classes.get(c));
                student.setRollNo(r + 1);
                student.setAdmissionDate(admissionDate);
                if (number == 1) {
                    student.setEmail("student@school.edu");
                    student.setUser(login);
                }
                all.add(student);
            }
        }
        all = studentRepository.saveAll(all);

        List<List<Student>> rosters = new ArrayList<>();
        for (int c = 0; c < classes.size(); c++) {
            rosters.add(all.subList(c * STUDENTS_PER_CLASS, (c + 1) * STUDENTS_PER_CLASS));
        }
        return rosters;
    }

    // ---------------------------------------------------------------- attendance

    /** The last 30 school days up to today (inside the year, no Friday or Saturday) for every student. */
    private int seedAttendance(List<SchoolClass> classes, List<List<Student>> rosters, Random random,
                               LocalDate today) {
        LocalDate yearStart = LocalDate.of(today.getYear(), 1, 1);
        List<AttendanceRecord> records = new ArrayList<>();
        for (LocalDate day : DemoPlan.lastSchoolDays(today, yearStart, DemoPlan.SCHOOL_DAYS)) {
            for (int c = 0; c < classes.size(); c++) {
                for (Student student : rosters.get(c)) {
                    boolean weak = student.getRollNo() == WEAK_ROLL;
                    AttendanceStatus status = DemoPlan.attendanceStatus(random, weak);
                    String remarks = status == AttendanceStatus.EXCUSED ? "Medical leave" : null;
                    records.add(new AttendanceRecord(student, classes.get(c), day, status, remarks));
                }
            }
        }
        attendanceRecordRepository.saveAll(records);
        return records.size();
    }

    // ---------------------------------------------------------------- exams

    /**
     * Half-Yearly (8-19 June, published 2 July): marks for everybody.
     * Annual (25 Nov - 10 Dec, draft): marks for everybody EXCEPT the Class 6-A Mathematics sheet.
     * Each student has one ability, so results look consistent between the two exams.
     */
    private int seedExams(AcademicYear year, List<SchoolClass> classes, List<List<Student>> rosters,
                          List<Subject> subjects, Random random) {
        int y = Integer.parseInt(year.getName());
        Exam halfYearly = new Exam(year, HALF_YEARLY, LocalDate.of(y, 6, 8), LocalDate.of(y, 6, 19));
        Instant publishedAt = LocalDate.of(y, 7, 2).atTime(LocalTime.of(10, 0)).atZone(clock.getZone()).toInstant();
        halfYearly.publish(DemoPlan.notAfter(publishedAt, clock.instant()));
        Exam annual = new Exam(year, ANNUAL, LocalDate.of(y, 11, 25), LocalDate.of(y, 12, 10));
        List<Exam> exams = examRepository.saveAll(List.of(halfYearly, annual));
        halfYearly = exams.get(0);
        annual = exams.get(1);

        // studentId -> ability, drawn in roll order so it is the same every time.
        Map<Long, Double> ability = new LinkedHashMap<>();
        rosters.forEach(roster -> roster.forEach(student -> ability.put(student.getId(), DemoPlan.ability(random))));

        List<Mark> marks = new ArrayList<>();
        for (int c = 0; c < classes.size(); c++) {
            boolean demoClass = classes.get(c).getName().equals(DEMO_SHEET_CLASS);
            for (Subject subject : subjects) {
                boolean demoSheet = demoClass && subject.getCode().equals(DEMO_SHEET_SUBJECT);
                for (Student student : rosters.get(c)) {
                    marks.add(mark(halfYearly, student, subject, ability.get(student.getId()), random));
                    if (!demoSheet) {
                        // A little better at the end of the year.
                        marks.add(mark(annual, student, subject, ability.get(student.getId()) + 0.02, random));
                    }
                }
            }
        }
        markRepository.saveAll(marks);
        return marks.size();
    }

    private static Mark mark(Exam exam, Student student, Subject subject, double ability, Random random) {
        if (DemoPlan.absentFromExam(random)) {
            return new Mark(exam, student, subject, null, true);
        }
        return new Mark(exam, student, subject, DemoPlan.marks(random, ability, subject.getFullMarks()), false);
    }

    // ---------------------------------------------------------------- fees

    /**
     * Per student: the session fee (5000, due 21 Jan, paid in cash) and tuition (1500) for July to December,
     * due on the 10th. Older tuition is mostly paid, recent tuition often unpaid, some part-paid with 500.
     * Payments are never dated after now.
     */
    private int seedFees(List<List<Student>> rosters, Random random, LocalDate today) {
        ZoneId zone = clock.getZone();
        Instant now = clock.instant();
        int year = today.getYear();
        List<FeeInvoice> invoices = new ArrayList<>();
        List<BigDecimal> payAmounts = new ArrayList<>();
        List<PaymentMethod> payMethods = new ArrayList<>();
        List<Instant> payTimes = new ArrayList<>();

        for (List<Student> roster : rosters) {
            for (Student student : roster) {
                LocalDate sessionDue = LocalDate.of(year, 1, 21);
                invoices.add(invoice(invoices.size() + 1, student, "Session fee " + year, SESSION_FEE, sessionDue));
                payAmounts.add(SESSION_FEE);
                payMethods.add(PaymentMethod.CASH);
                payTimes.add(DemoPlan.paidAt(random, sessionDue, zone, now));

                for (YearMonth month : DemoPlan.tuitionMonths(year)) {
                    LocalDate due = month.atDay(10);
                    String title = "Tuition " + month.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                            + " " + year;
                    invoices.add(invoice(invoices.size() + 1, student, title, TUITION_FEE, due));
                    PaymentState state = DemoPlan.tuitionState(random, due, today);
                    payAmounts.add(state == PaymentState.PAID ? TUITION_FEE
                            : state == PaymentState.PART_PAID ? PART_PAYMENT : null);
                    payMethods.add(TUITION_METHODS[random.nextInt(TUITION_METHODS.length)]);
                    payTimes.add(DemoPlan.paidAt(random, due, zone, now));
                }
            }
        }

        // The invoice keeps amountPaid and its status (PAID / PARTIALLY_PAID) in step with its payment.
        for (int i = 0; i < invoices.size(); i++) {
            if (payAmounts.get(i) != null) {
                invoices.get(i).applyPayment(payAmounts.get(i));
            }
        }
        invoices = feeInvoiceRepository.saveAll(invoices);

        List<Payment> payments = new ArrayList<>();
        for (int i = 0; i < invoices.size(); i++) {
            if (payAmounts.get(i) != null) {
                String ref = String.format("DEMO-%05d", payments.size() + 1);
                payments.add(new Payment(invoices.get(i), payAmounts.get(i), payMethods.get(i), ref,
                        "demo-" + ref.toLowerCase(Locale.ROOT), payTimes.get(i)));
            }
        }
        paymentRepository.saveAll(payments);
        return invoices.size();
    }

    /** Fixed invoice numbers (INV-202607-D00002) so both fresh databases get the same ones. */
    private static FeeInvoice invoice(int number, Student student, String title, BigDecimal amount, LocalDate due) {
        String invoiceNo = "INV-" + due.format(INVOICE_MONTH) + String.format("-D%05d", number);
        return new FeeInvoice(invoiceNo, student, title, amount, BigDecimal.ZERO, due);
    }

    // ---------------------------------------------------------------- notices

    private void seedNotices(LocalDate today) {
        Instant now = clock.instant();
        Instant newYear = LocalDate.of(today.getYear(), 1, 1).atTime(LocalTime.of(9, 0))
                .atZone(clock.getZone()).toInstant();
        noticeRepository.saveAll(List.of(
                new Notice("Welcome to the new academic year",
                        "Welcome back to Greenfield High School. Classes run Sunday to Thursday, 8:00 to 13:30.",
                        NoticeAudience.ALL, true, DemoPlan.notAfter(newYear, now)),
                new Notice("Annual Examination schedule",
                        "The Annual Examination runs from 25 November to 10 December. "
                                + "The routine is on the notice board; bring your admit card every day.",
                        NoticeAudience.STUDENTS, false, now.minus(Duration.ofDays(14))),
                new Notice("Staff meeting on Sunday",
                        "All teachers please join the staff meeting in the library on Sunday at 14:00.",
                        NoticeAudience.TEACHERS, false, now.minus(Duration.ofDays(2))),
                new Notice("Victory Day holiday",
                        "The school is closed on 16 December for Victory Day.",
                        NoticeAudience.ALL, false, now.minus(Duration.ofDays(1)))));
    }

    // ---------------------------------------------------------------- helpers

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }

    private record SubjectSpec(String code, String name, int fullMarks, int passMarks) {
    }

    private record TeacherSpec(String employeeNo, String firstName, String lastName, String designation,
                               LocalDate joiningDate) {
        String email() {
            return (firstName + "." + lastName + "@school.edu").toLowerCase(Locale.ROOT);
        }
    }

    private record StudentSpec(String firstName, String lastName, Gender gender, String fatherName) {
    }
}
