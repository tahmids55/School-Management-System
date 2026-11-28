package com.school.demo;

import com.school.academic.AcademicYearRepository;
import com.school.academic.ClassSubjectRepository;
import com.school.academic.SchoolClassRepository;
import com.school.academic.SubjectRepository;
import com.school.attendance.AttendanceRecordRepository;
import com.school.exam.ExamRepository;
import com.school.exam.MarkRepository;
import com.school.fee.FeeInvoiceRepository;
import com.school.fee.PaymentRepository;
import com.school.notice.NoticeRepository;
import com.school.student.StudentRepository;
import com.school.teacher.TeacherRepository;
import com.school.user.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The guard rules of F32: when the seeder may run at all. The full school is checked in the integration test. */
@ExtendWith(MockitoExtension.class)
class DemoDataSeederTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-26T04:00:00Z"), ZoneId.of("Asia/Dhaka"));

    @Mock private AppUserRepository appUserRepository;
    @Mock private AcademicYearRepository academicYearRepository;
    @Mock private SubjectRepository subjectRepository;
    @Mock private TeacherRepository teacherRepository;
    @Mock private SchoolClassRepository schoolClassRepository;
    @Mock private ClassSubjectRepository classSubjectRepository;
    @Mock private StudentRepository studentRepository;
    @Mock private AttendanceRecordRepository attendanceRecordRepository;
    @Mock private ExamRepository examRepository;
    @Mock private MarkRepository markRepository;
    @Mock private FeeInvoiceRepository feeInvoiceRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private NoticeRepository noticeRepository;
    @Mock private PasswordEncoder passwordEncoder;

    private DemoDataSeeder seeder(boolean enabled, String adminPassword, String demoPassword) {
        return new DemoDataSeeder(enabled, adminPassword, demoPassword, appUserRepository, academicYearRepository,
                subjectRepository, teacherRepository, schoolClassRepository, classSubjectRepository,
                studentRepository, attendanceRecordRepository, examRepository, markRepository,
                feeInvoiceRepository, paymentRepository, noticeRepository, passwordEncoder, CLOCK);
    }

    @Test
    void disabled_touchesNothing() {
        seeder(false, "AdminPass#1", "DemoPass#1").run(null);

        verifyNoInteractions(academicYearRepository, studentRepository, teacherRepository, appUserRepository,
                passwordEncoder);
    }

    @Test
    void existingAcademicYear_seedsNothing_evenWithoutPasswords() {
        when(academicYearRepository.count()).thenReturn(1L);

        assertThat(seeder(true, "", "").seed()).isFalse();

        verify(subjectRepository, never()).saveAll(anyIterable());
        verifyNoInteractions(passwordEncoder, appUserRepository);
    }

    @Test
    void existingStudent_seedsNothing() {
        when(studentRepository.count()).thenReturn(3L);

        assertThat(seeder(true, "AdminPass#1", "DemoPass#1").seed()).isFalse();

        verifyNoInteractions(passwordEncoder, appUserRepository);
    }

    @Test
    void existingTeacher_seedsNothing() {
        when(teacherRepository.count()).thenReturn(1L);

        assertThat(seeder(true, "AdminPass#1", "DemoPass#1").seed()).isFalse();

        verifyNoInteractions(passwordEncoder, appUserRepository);
    }

    @Test
    void emptySchool_withoutDemoPassword_failsWithTheExactMessage_andSavesNothing() {
        assertThatThrownBy(() -> seeder(true, "AdminPass#1", " ").seed())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Set ADMIN_PASSWORD and DEMO_PASSWORD to seed demo data");

        verify(academicYearRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder, appUserRepository);
    }

    @Test
    void emptySchool_withoutAdminPassword_failsWithTheExactMessage() {
        assertThatThrownBy(() -> seeder(true, null, "DemoPass#1").seed())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(DemoDataSeeder.MISSING_PASSWORDS);

        verify(academicYearRepository, never()).save(any());
    }
}
