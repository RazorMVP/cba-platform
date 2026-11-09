package com.cba.payment;

import com.cba.account.Account;
import com.cba.account.AccountGlPosting;
import com.cba.account.AccountService;
import com.cba.account.AccountStatus;
import com.cba.account.TransactionType;
import com.cba.audit.AuditLogService;
import com.cba.payment.ExternalPaymentOutcomeService.Outcome;
import com.cba.payment.dto.PaymentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.AuditorAware;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExternalPaymentOutcomeService — settle or return a PROCESSING external payment (backlog 1)")
class ExternalPaymentOutcomeServiceTest {

    @Mock PaymentRepository paymentRepository;
    @Mock AccountService accountService;
    @Mock AccountGlPosting accountGlPosting;
    @Mock AuditLogService auditLogService;
    @Mock PaymentService paymentService;
    @Mock AuditorAware<String> auditorAware;

    @InjectMocks ExternalPaymentOutcomeService service;

    private Account source;
    private Payment payment;

    @BeforeEach
    void setUp() {
        source = new Account();
        source.setId(UUID.randomUUID());
        source.setStatus(AccountStatus.ACTIVE);
        source.setCurrencyCode("USD");
        source.setBalance(new BigDecimal("900.00"));

        payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setReferenceNumber("EXT-AAAA1111");
        payment.setNetworkReference("UETR-1");
        payment.setPaymentType(PaymentType.EXTERNAL_PAYMENT);
        payment.setStatus(PaymentStatus.PROCESSING);
        payment.setAmount(new BigDecimal("100.00"));
        payment.setCurrencyCode("USD");
        payment.setSourceAccount(source);
        payment.setCreatedBy("teller1");

        lenient().when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(paymentService.toResponse(any())).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            PaymentResponse r = mock(PaymentResponse.class);
            lenient().when(r.status()).thenReturn(p.getStatus());
            lenient().when(r.id()).thenReturn(p.getId());
            return r;
        });
    }

    @Test
    @DisplayName("SETTLED → DR 2400 / CR nostro, COMPLETED, no account movement")
    void settled() {
        when(paymentRepository.findByReferenceForUpdate("EXT-AAAA1111")).thenReturn(Optional.of(payment));

        var r = service.applyCallback("EXT-AAAA1111", null, Outcome.SETTLED, null);

        assertThat(r.repeat()).isFalse();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(payment.getSettledAt()).isNotNull();
        assertThat(payment.getOutcomeSource()).isEqualTo("CALLBACK");
        verify(accountGlPosting).postExternalPaymentSettlement(eq(new BigDecimal("100.00")), eq("USD"), any(), any(),
                eq("EXT-AAAA1111"), eq(payment.getId()));
        verifyNoInteractions(accountService);
    }

    @Test
    @DisplayName("RETURNED → source credited back, DR 2400 / CR deposit, RETURNED with the reason")
    void returned() {
        when(paymentRepository.findByNetworkReferenceForUpdate("UETR-1")).thenReturn(Optional.of(payment));
        when(accountService.creditForSubledger(eq(source.getId()), eq(new BigDecimal("100.00")), eq(TransactionType.REVERSAL),
                any(), eq("RET-EXT-AAAA1111"), any()))
                .thenReturn(new AccountService.BalanceChange(source, new BigDecimal("900.00"), null));

        service.applyCallback(null, "UETR-1", Outcome.RETURNED, "AC04 closed account");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.RETURNED);
        assertThat(payment.getFailureReason()).isEqualTo("AC04 closed account");
        verify(accountGlPosting).postExternalPaymentMovement(eq(source), eq(new BigDecimal("900.00")), any(), any(),
                eq("RET-EXT-AAAA1111"), eq(payment.getId()));
        verify(accountGlPosting, never()).postExternalPaymentSettlement(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("the same outcome again → repeat, nothing posted twice")
    void repeat() {
        payment.setStatus(PaymentStatus.COMPLETED);
        when(paymentRepository.findByReferenceForUpdate("EXT-AAAA1111")).thenReturn(Optional.of(payment));

        assertThat(service.applyCallback("EXT-AAAA1111", null, Outcome.SETTLED, null).repeat()).isTrue();
        verifyNoInteractions(accountGlPosting, accountService);
    }

    @Test
    @DisplayName("a different outcome after the payment left PROCESSING → 409 PAYMENT_NOT_PROCESSING")
    void notProcessing() {
        payment.setStatus(PaymentStatus.COMPLETED);
        when(paymentRepository.findByReferenceForUpdate("EXT-AAAA1111")).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.applyCallback("EXT-AAAA1111", null, Outcome.RETURNED, "late"))
                .hasFieldOrPropertyWithValue("errorCode", "PAYMENT_NOT_PROCESSING");
        verifyNoInteractions(accountGlPosting, accountService);
    }

    @Test
    @DisplayName("our reference and a different network reference → 409 REFERENCE_MISMATCH")
    void referenceMismatch() {
        when(paymentRepository.findByReferenceForUpdate("EXT-AAAA1111")).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.applyCallback("EXT-AAAA1111", "UETR-OTHER", Outcome.SETTLED, null))
                .hasFieldOrPropertyWithValue("errorCode", "REFERENCE_MISMATCH");
    }

    @Test
    @DisplayName("an internal transfer can't be settled this way")
    void notExternal() {
        payment.setPaymentType(PaymentType.INTERNAL_TRANSFER);
        when(paymentRepository.findByReferenceForUpdate("EXT-AAAA1111")).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.applyCallback("EXT-AAAA1111", null, Outcome.SETTLED, null))
                .hasFieldOrPropertyWithValue("errorCode", "NOT_AN_EXTERNAL_PAYMENT");
    }

    @Test
    @DisplayName("manual: not by the initiator (403 SAME_USER); by someone else → applied as MANUAL")
    void manualFourEyes() {
        when(paymentRepository.findByIdForUpdate(payment.getId())).thenReturn(Optional.of(payment));
        when(auditorAware.getCurrentAuditor()).thenReturn(Optional.of("teller1"), Optional.of("ops-admin"));

        assertThatThrownBy(() -> service.applyManual(payment.getId(), Outcome.SETTLED, "confirmed by phone"))
                .hasFieldOrPropertyWithValue("errorCode", "SAME_USER");

        service.applyManual(payment.getId(), Outcome.SETTLED, "confirmed by phone");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(payment.getOutcomeSource()).isEqualTo("MANUAL");
    }

    @Test
    @DisplayName("manual: reason required; FAILED isn't offered")
    void manualValidation() {
        assertThatThrownBy(() -> service.applyManual(payment.getId(), Outcome.SETTLED, " "))
                .hasFieldOrPropertyWithValue("errorCode", "REASON_REQUIRED");
        assertThatThrownBy(() -> service.applyManual(payment.getId(), Outcome.FAILED, "x"))
                .hasFieldOrPropertyWithValue("errorCode", "INVALID_OUTCOME");
    }
}
