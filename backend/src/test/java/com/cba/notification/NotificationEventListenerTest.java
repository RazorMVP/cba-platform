package com.cba.notification;

import com.cba.customer.Customer;
import com.cba.customer.CustomerRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationEventListener — loan-approval email resolution")
class NotificationEventListenerTest {

    @Mock JavaMailSender mailSender;
    @Mock InAppNotificationService inAppService;
    @Mock CustomerRepository customerRepository;

    @InjectMocks NotificationEventListener listener;

    @Test
    @DisplayName("loan approved sends email to the resolved customer address")
    void loanApproved_sendsToResolvedCustomerEmail() {
        UUID customerId = UUID.randomUUID();
        Customer customer = new Customer();
        customer.setEmail("alice@example.com");
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));

        listener.onLoanApproved(new LoanEvent(this, UUID.randomUUID(), customerId, LoanEvent.Type.APPROVED));

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getTo()).containsExactly("alice@example.com");
    }

    @Test
    @DisplayName("loan approved with no email on file skips the mail but still pushes in-app")
    void loanApproved_noEmail_skipsMail() {
        UUID customerId = UUID.randomUUID();
        when(customerRepository.findById(customerId)).thenReturn(Optional.empty());

        listener.onLoanApproved(new LoanEvent(this, UUID.randomUUID(), customerId, LoanEvent.Type.APPROVED));

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
        verify(inAppService).push(any(), any(), any(), any(), any(), any());
    }
}
