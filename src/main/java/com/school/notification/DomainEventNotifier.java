package com.school.notification;

import com.school.exam.ResultsPublishedEvent;
import com.school.fee.PaymentReceivedEvent;
import com.school.notice.NoticeAudience;
import com.school.notice.NoticePublishedEvent;
import com.school.notification.sms.SmsSender;
import com.school.user.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Objects;

/**
 * Observer pattern: notices, payments and exams PUBLISH events and do not know who listens.
 * This class SUBSCRIBES and turns each event into in-app notifications (and SMS to guardians).
 *
 * AFTER_COMMIT: it runs only when the publisher's transaction really committed, so a rolled-back
 * payment or notice never notifies anybody. The rows are written in a NEW transaction
 * ({@link NotificationService#notifyUsers}, REQUIRES_NEW). Every failure is caught and logged:
 * the original action is already committed and must never be undone or reported as failed.
 */
@Component
public class DomainEventNotifier {

    static final String NOTICE_TITLE = "New notice";
    static final String NOTICE_LINK = "/notices";
    static final String RESULTS_TITLE = "Results published";
    static final String RESULTS_LINK = "/my/results";
    static final String PAYMENT_TITLE = "Payment received";
    static final String PAYMENT_LINK = "/my/fees";

    private static final Logger log = LoggerFactory.getLogger(DomainEventNotifier.class);

    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final SmsSender smsSender;

    public DomainEventNotifier(
            NotificationRepository notificationRepository,
            NotificationService notificationService,
            SmsSender smsSender
    ) {
        this.notificationRepository = notificationRepository;
        this.notificationService = notificationService;
        this.smsSender = smsSender;
    }

    /** ALL = every enabled teacher and student (admins wrote it); otherwise the enabled users of that role. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNoticePublished(NoticePublishedEvent event) {
        try {
            List<Long> userIds = notificationRepository.findEnabledUserIdsByRoles(rolesFor(event.audience()));
            notificationService.notifyUsers(userIds, NOTICE_TITLE, event.title(), NOTICE_LINK);
        } catch (RuntimeException ex) {
            log.error("Could not notify users about notice {}", event.noticeId(), ex);
        }
    }

    /** Every active student of the exam year's classes; each guardian gets an SMS too. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onResultsPublished(ResultsPublishedEvent event) {
        try {
            List<Recipient> recipients = notificationRepository.findExamRecipients(event.examId());
            notificationService.notifyUsers(
                    loginsOf(recipients),
                    RESULTS_TITLE,
                    "Results of " + event.examName() + " are now available.",
                    RESULTS_LINK);
            for (Recipient recipient : recipients) {
                smsSender.send(recipient.guardianPhone(),
                        "Results of " + event.examName() + " for " + recipient.firstName() + " are published.");
            }
        } catch (RuntimeException ex) {
            log.error("Could not notify students about results of exam {}", event.examId(), ex);
        }
    }

    /** The paying student's login (if any) and, with the same text, the guardian by SMS. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentReceived(PaymentReceivedEvent event) {
        try {
            Recipient recipient = notificationRepository.findStudentRecipient(event.studentId()).orElse(null);
            if (recipient == null) {
                return;
            }
            String text = "Payment of BDT " + event.amount().toPlainString() + " received for invoice "
                    + event.invoiceNo() + ". Ref: " + event.transactionRef();
            notificationService.notifyUsers(loginsOf(List.of(recipient)), PAYMENT_TITLE, text, PAYMENT_LINK);
            smsSender.send(recipient.guardianPhone(), text);
        } catch (RuntimeException ex) {
            log.error("Could not notify about payment {}", event.paymentId(), ex);
        }
    }

    static List<Role> rolesFor(NoticeAudience audience) {
        return switch (audience) {
            case ALL -> List.of(Role.TEACHER, Role.STUDENT);
            case TEACHERS -> List.of(Role.TEACHER);
            case STUDENTS -> List.of(Role.STUDENT);
        };
    }

    /** Students without a login get no in-app notification (their guardian still gets the SMS). */
    private static List<Long> loginsOf(List<Recipient> recipients) {
        return recipients.stream().map(Recipient::userId).filter(Objects::nonNull).toList();
    }
}
