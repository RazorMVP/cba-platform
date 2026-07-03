package com.cba.notification;

import com.cba.customer.Customer;
import com.cba.customer.CustomerRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * End-to-end integration test for the email notification path against a real MailHog
 * container (SMTP sink). Drives the actual {@link NotificationEventListener#onLoanApproved}
 * → {@code JavaMailSender.send} over real SMTP, then asserts the message landed via
 * MailHog's HTTP API.
 *
 * <p>This is the same {@code JavaMailSender} mechanism production uses (dev points at
 * MailHog; prod at a real SMTP relay via {@code MAIL_*}). The unit tests never send mail;
 * this proves the SMTP transport, envelope, subject, and recipient resolution work.
 */
@Testcontainers
@DisplayName("Email notifications — end-to-end against a MailHog container")
class MailHogEmailIntegrationTest {

    @Container
    static final GenericContainer<?> MAILHOG =
            new GenericContainer<>(DockerImageName.parse("mailhog/mailhog:v1.0.1"))
                    .withExposedPorts(1025, 8025)
                    .waitingFor(Wait.forHttp("/api/v2/messages").forPort(8025).forStatusCode(200));

    private final RestTemplate http = new RestTemplate();

    @Test
    @DisplayName("loan-approved event delivers a real email captured by MailHog")
    void loanApproved_sendsEmail() throws Exception {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(MAILHOG.getHost());
        mailSender.setPort(MAILHOG.getMappedPort(1025));

        UUID customerId = UUID.randomUUID();
        Customer customer = new Customer();
        customer.setId(customerId);
        customer.setEmail("borrower@example.com");

        CustomerRepository customerRepository = mock(CustomerRepository.class);
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        InAppNotificationService inApp = mock(InAppNotificationService.class);

        NotificationEventListener listener =
                new NotificationEventListener(mailSender, inApp, customerRepository);

        // Direct call → synchronous (the @Async proxy is not involved in a plain new).
        listener.onLoanApproved(new LoanEvent(this, UUID.randomUUID(), customerId, LoanEvent.Type.APPROVED));

        String messages = pollMailHog();
        assertThat(messages).contains("Your loan has been approved");
        assertThat(messages).contains("borrower@example.com");
    }

    /**
     * Poll MailHog's message store until the mail arrives (SMTP delivery is near-instant).
     * MailHog serves {@code Content-Type: text/json}, so we read the raw String body rather
     * than let the JSON converter reject the non-standard content type.
     */
    private String pollMailHog() throws InterruptedException {
        String url = "http://" + MAILHOG.getHost() + ":" + MAILHOG.getMappedPort(8025) + "/api/v2/messages";
        String body = "";
        for (int i = 0; i < 25; i++) {
            body = http.getForObject(url, String.class);
            if (body != null && body.contains("Your loan has been approved")) {
                return body;
            }
            Thread.sleep(200);
        }
        return body; // assertion will report the (empty) store
    }
}
