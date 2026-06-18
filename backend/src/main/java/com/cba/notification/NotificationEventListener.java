package com.cba.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.cba.customer.Customer;
import com.cba.customer.CustomerRepository;

import java.util.UUID;

/**
 * Listens for domain events and sends notifications asynchronously.
 * @Async ensures notifications never block the calling transaction.
 * In dev: emails go to MailHog (http://localhost:8025).
 * In prod: configure SMTP via MAIL_HOST/MAIL_PORT environment variables.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventListener {

    private final JavaMailSender mailSender;
    private final InAppNotificationService inAppService;
    private final CustomerRepository customerRepository;

    @EventListener
    @Async
    public void onAccountOpened(AccountEvent event) {
        if (event.getType() == AccountEvent.Type.OPENED) {
            log.info("Notification: account opened — accountId={}", event.getAccountId());
            inAppService.push(
                InAppNotification.Type.ACCOUNT_OPENED,
                InAppNotification.Severity.INFO,
                "Account opened",
                "A new savings account has been opened.",
                "ACCOUNT", event.getAccountId()
            );
        } else if (event.getType() == AccountEvent.Type.CLOSED) {
            inAppService.push(
                InAppNotification.Type.ACCOUNT_CLOSED,
                InAppNotification.Severity.INFO,
                "Account closed",
                "Account has been closed successfully.",
                "ACCOUNT", event.getAccountId()
            );
        } else if (event.getType() == AccountEvent.Type.FROZEN) {
            inAppService.push(
                InAppNotification.Type.ACCOUNT_FROZEN,
                InAppNotification.Severity.WARNING,
                "Account frozen",
                "Account has been frozen pending review.",
                "ACCOUNT", event.getAccountId()
            );
        }
    }

    @EventListener
    @Async
    public void onLoanApproved(LoanEvent event) {
        if (event.getType() == LoanEvent.Type.APPROVED) {
            log.info("Notification: loan approved — loanId={}", event.getLoanId());
            inAppService.push(
                InAppNotification.Type.LOAN_APPROVED,
                InAppNotification.Severity.INFO,
                "Loan approved",
                "Loan application has been approved and is ready for disbursement.",
                "LOAN", event.getLoanId()
            );
            String email = resolveCustomerEmail(event.getCustomerId());
            if (email != null && !email.isBlank()) {
                sendSimpleMail(
                    "noreply@cba.com",
                    email,
                    "Your loan has been approved",
                    "Congratulations! Your loan application has been approved."
                );
            } else {
                log.warn("Loan approved but no email on file for customerId={} — email skipped",
                    event.getCustomerId());
            }
        } else if (event.getType() == LoanEvent.Type.DISBURSED) {
            inAppService.push(
                InAppNotification.Type.LOAN_DISBURSED,
                InAppNotification.Severity.INFO,
                "Loan disbursed",
                "Loan funds have been disbursed to the linked account.",
                "LOAN", event.getLoanId()
            );
        }
    }

    @EventListener
    @Async
    public void onLoanInArrears(LoanEvent event) {
        if (event.getType() == LoanEvent.Type.IN_ARREARS) {
            log.warn("Notification: loan in arrears — loanId={}", event.getLoanId());
            inAppService.push(
                InAppNotification.Type.LOAN_IN_ARREARS,
                InAppNotification.Severity.WARNING,
                "Loan overdue",
                "A loan has overdue installments. Action required.",
                "LOAN", event.getLoanId()
            );
        }
    }

    /**
     * Resolves a customer's email through CustomerRepository so the JPA
     * EncryptedStringConverter decrypts it in the correct persistence context.
     * Returns null if the customer or email is absent — caller skips the send.
     */
    private String resolveCustomerEmail(UUID customerId) {
        if (customerId == null) return null;
        return customerRepository.findById(customerId)
                .map(Customer::getEmail)
                .orElse(null);
    }

    private void sendSimpleMail(String from, String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
        } catch (Exception e) {
            log.error("Failed to send email notification: {}", e.getMessage());
        }
    }
}
