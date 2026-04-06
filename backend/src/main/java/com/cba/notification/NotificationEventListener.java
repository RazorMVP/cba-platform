package com.cba.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

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

    @EventListener
    @Async
    public void onAccountOpened(AccountEvent event) {
        if (event.getType() == AccountEvent.Type.OPENED) {
            log.info("Notification: account opened — accountId={}", event.getAccountId());
            // In production: look up customer email and send welcome email
            // Omitted here to avoid a circular dependency via AccountRepository
        }
    }

    @EventListener
    @Async
    public void onLoanApproved(LoanEvent event) {
        if (event.getType() == LoanEvent.Type.APPROVED) {
            log.info("Notification: loan approved — loanId={}", event.getLoanId());
            sendSimpleMail(
                "noreply@cba.com",
                "customer@cba.com", // TODO: resolve from customerId
                "Your loan has been approved",
                "Congratulations! Your loan application has been approved."
            );
        }
    }

    @EventListener
    @Async
    public void onLoanInArrears(LoanEvent event) {
        if (event.getType() == LoanEvent.Type.IN_ARREARS) {
            log.warn("Notification: loan in arrears — loanId={}", event.getLoanId());
        }
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
