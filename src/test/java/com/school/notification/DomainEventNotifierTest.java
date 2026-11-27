package com.school.notification;

import com.school.exam.ResultsPublishedEvent;
import com.school.fee.PaymentReceivedEvent;
import com.school.notice.NoticeAudience;
import com.school.notice.NoticePublishedEvent;
import com.school.notification.sms.SmsSender;
import com.school.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DomainEventNotifierTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private SmsSender smsSender;

    private DomainEventNotifier notifier;

    @BeforeEach
    void setUp() {
        notifier = new DomainEventNotifier(notificationRepository, notificationService, smsSender);
    }

    @Test
    void rolesFor_allMeansTeachersAndStudents_neverAdmins() {
        assertThat(DomainEventNotifier.rolesFor(NoticeAudience.ALL)).containsExactly(Role.TEACHER, Role.STUDENT);
        assertThat(DomainEventNotifier.rolesFor(NoticeAudience.TEACHERS)).containsExactly(Role.TEACHER);
        assertThat(DomainEventNotifier.rolesFor(NoticeAudience.STUDENTS)).containsExactly(Role.STUDENT);
    }

    @Test
    void notice_notifiesEnabledUsersOfTheAudience_withTitleAsMessage() {
        when(notificationRepository.findEnabledUserIdsByRoles(List.of(Role.TEACHER))).thenReturn(List.of(4L, 5L));

        notifier.onNoticePublished(new NoticePublishedEvent(1L, "Sports day", NoticeAudience.TEACHERS));

        verify(notificationService).notifyUsers(List.of(4L, 5L), "New notice", "Sports day", "/notices");
        verifyNoInteractions(smsSender);
    }

    @Test
    void results_notifyLoginsOnly_andSmsEveryGuardianWithFirstName() {
        when(notificationRepository.findExamRecipients(9L)).thenReturn(List.of(
                new Recipient(21L, "Amina", "01711000001"),
                new Recipient(null, "Babul", "01711000002")));

        notifier.onResultsPublished(new ResultsPublishedEvent(9L, "Half Yearly"));

        verify(notificationService).notifyUsers(List.of(21L), "Results published",
                "Results of Half Yearly are now available.", "/my/results");
        verify(smsSender).send("01711000001", "Results of Half Yearly for Amina are published.");
        verify(smsSender).send("01711000002", "Results of Half Yearly for Babul are published.");
    }

    @Test
    void payment_sameTextInAppAndBySms() {
        when(notificationRepository.findStudentRecipient(3L))
                .thenReturn(Optional.of(new Recipient(21L, "Amina", "01711000001")));

        notifier.onPaymentReceived(new PaymentReceivedEvent(
                50L, 7L, 3L, new BigDecimal("400.00"), "INV-202611-ABCDEF", "CASH-1"));

        String text = "Payment of BDT 400.00 received for invoice INV-202611-ABCDEF. Ref: CASH-1";
        verify(notificationService).notifyUsers(List.of(21L), "Payment received", text, "/my/fees");
        verify(smsSender).send("01711000001", text);
    }

    @Test
    void payment_unknownStudent_doesNothing() {
        when(notificationRepository.findStudentRecipient(3L)).thenReturn(Optional.empty());

        notifier.onPaymentReceived(new PaymentReceivedEvent(50L, 7L, 3L, BigDecimal.ONE, "INV", "REF"));

        verifyNoInteractions(notificationService, smsSender);
    }

    @Test
    void failure_isCaughtAndLogged_neverThrown() {
        when(notificationRepository.findEnabledUserIdsByRoles(any())).thenReturn(List.of(4L));
        when(notificationService.notifyUsers(any(), anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("database down"));

        assertThatCode(() -> notifier.onNoticePublished(
                new NoticePublishedEvent(1L, "Sports day", NoticeAudience.ALL))).doesNotThrowAnyException();
    }
}
