package com.cba.openbanking;

import com.cba.account.Account;
import com.cba.account.AccountRepository;
import com.cba.audit.AuditLogService;
import com.cba.common.exception.CbaException;
import com.cba.customer.Customer;
import com.cba.openbanking.dto.DomesticPaymentRequest;
import com.cba.payment.PaymentService;
import com.cba.payment.dto.PaymentResponse;
import com.cba.payment.dto.TransferRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PaymentInitiationService — one payment per consent, exactly as authorised (backlog 24)")
class PaymentInitiationServiceTest {

    @Mock ConsentRepository consentRepository;
    @Mock AccountRepository accountRepository;
    @Mock PaymentService paymentService;
    @Mock AuditLogService auditLogService;
    @InjectMocks PaymentInitiationService service;

    static final UUID ORG = ConsentServiceTest.ORG;
    static final String CONSENT = "ob-pay000000000000001";

    UUID customerId = UUID.randomUUID();
    UUID source = UUID.randomUUID();
    UUID creditor = UUID.randomUUID();
    UUID paymentId = UUID.randomUUID();
    OpenBankingConsent consent;

    @BeforeEach
    void setUp() {
        ConsentServiceTest.asTpp(ORG);
        Customer c = new Customer();
        c.setId(customerId);
        consent = new OpenBankingConsent();
        consent.setConsentId(CONSENT);
        consent.setCustomer(c);
        consent.setTppClientId(ORG.toString());
        consent.setScopes(List.of("payments"));
        consent.setStatus(ConsentStatus.AUTHORISED);
        consent.setInstructedAmount(new BigDecimal("25.0000"));
        consent.setInstructedCurrency("USD");
        consent.setCreditorAccountId(creditor);
        consent.setPaymentReference("INV-1");
        when(consentRepository.findByConsentIdForUpdate(CONSENT)).thenReturn(Optional.of(consent));
        when(accountRepository.findById(source)).thenReturn(Optional.of(account(customerId, "USD")));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private Account account(UUID owner, String currency) {
        Customer c = new Customer();
        c.setId(owner);
        Account a = new Account();
        a.setId(source);
        a.setCustomer(c);
        a.setCurrencyCode(currency);
        return a;
    }

    private DomesticPaymentRequest request(String amount, String currency, UUID destination, String reference) {
        return new DomesticPaymentRequest(CONSENT, source, destination, new BigDecimal(amount), currency, reference, null);
    }

    private DomesticPaymentRequest matching() {
        return request("25.00", "usd", creditor, "INV-1");
    }

    private PaymentResponse payment() {
        PaymentResponse p = mock(PaymentResponse.class);
        when(p.id()).thenReturn(paymentId);
        when(p.sourceAccountId()).thenReturn(source);
        return p;
    }

    @Test
    @DisplayName("matching request: transfers the consent's amount to its creditor, then CONSUMED with the payment id")
    void pays() {
        PaymentResponse p = payment();
        when(paymentService.transfer(any(), anyString())).thenReturn(p);

        PaymentInitiationService.Initiated result = service.initiate(matching());

        ArgumentCaptor<TransferRequest> transfer = ArgumentCaptor.forClass(TransferRequest.class);
        verify(paymentService).transfer(transfer.capture(), eq("open-banking:" + CONSENT));
        assertThat(transfer.getValue().amount()).isEqualByComparingTo("25");
        assertThat(transfer.getValue().destinationAccountId()).isEqualTo(creditor);
        assertThat(transfer.getValue().sourceAccountId()).isEqualTo(source);
        assertThat(transfer.getValue().description()).isEqualTo("INV-1");
        assertThat(result.replay()).isFalse();
        assertThat(consent.getStatus()).isEqualTo(ConsentStatus.CONSUMED);
        assertThat(consent.getPaymentId()).isEqualTo(paymentId);
        assertThat(consent.getConsumedAt()).isNotNull();
        verify(consentRepository).save(consent);
    }

    @ParameterizedTest(name = "{0} differs → 400, no transfer")
    @ValueSource(strings = {"amount", "currency", "destinationAccountId", "reference"})
    @DisplayName("a request that differs from the consent: 400 PAYMENT_DOES_NOT_MATCH_CONSENT naming the field")
    void mismatch(String field) {
        DomesticPaymentRequest r = switch (field) {
            case "amount" -> request("25.01", "USD", creditor, "INV-1");
            case "currency" -> request("25.00", "EUR", creditor, "INV-1");
            case "destinationAccountId" -> request("25.00", "USD", UUID.randomUUID(), "INV-1");
            default -> request("25.00", "USD", creditor, "INV-2");
        };

        assertThatThrownBy(() -> service.initiate(r))
                .hasFieldOrPropertyWithValue("errorCode", "PAYMENT_DOES_NOT_MATCH_CONSENT")
                .hasMessageContaining(field);
        verifyNoInteractions(paymentService);
        assertThat(consent.getStatus()).isEqualTo(ConsentStatus.AUTHORISED);
    }

    @Test
    @DisplayName("a consent naming its debtor account: payment from another of the customer's accounts → 400 (sourceAccountId)")
    void debtorMismatch() {
        consent.setDebtorAccountId(UUID.randomUUID());

        assertThatThrownBy(() -> service.initiate(matching()))
                .hasFieldOrPropertyWithValue("errorCode", "PAYMENT_DOES_NOT_MATCH_CONSENT")
                .hasMessageContaining("sourceAccountId");
        verifyNoInteractions(paymentService);
    }

    @Test
    @DisplayName("source account of another customer: 404 ACCOUNT_NOT_FOUND, no transfer")
    void foreignSource() {
        when(accountRepository.findById(source)).thenReturn(Optional.of(account(UUID.randomUUID(), "USD")));

        assertThatThrownBy(() -> service.initiate(matching()))
                .hasFieldOrPropertyWithValue("errorCode", "ACCOUNT_NOT_FOUND");
        verifyNoInteractions(paymentService);
    }

    @Test
    @DisplayName("another org's consent: 404 CONSENT_NOT_FOUND, no transfer")
    void otherOrg() {
        ConsentServiceTest.asTpp(ConsentServiceTest.OTHER_ORG);

        assertThatThrownBy(() -> service.initiate(matching()))
                .hasFieldOrPropertyWithValue("errorCode", "CONSENT_NOT_FOUND");
        verifyNoInteractions(paymentService);
    }

    @Test
    @DisplayName("source account in another currency than the consent: 400 CURRENCY_MISMATCH")
    void sourceCurrency() {
        when(accountRepository.findById(source)).thenReturn(Optional.of(account(customerId, "EUR")));

        assertThatThrownBy(() -> service.initiate(matching()))
                .hasFieldOrPropertyWithValue("errorCode", "CURRENCY_MISMATCH");
        verifyNoInteractions(paymentService);
    }

    @Test
    @DisplayName("not yet authorised by the customer: 403 CONSENT_NOT_AUTHORISED")
    void notAuthorised() {
        consent.setStatus(ConsentStatus.AWAITING_AUTHORISATION);

        assertThatThrownBy(() -> service.initiate(matching()))
                .hasFieldOrPropertyWithValue("errorCode", "CONSENT_NOT_AUTHORISED");
        verifyNoInteractions(paymentService);
    }

    @Test
    @DisplayName("a payments consent from before V65 (no initiation): 403 CONSENT_HAS_NO_INITIATION")
    void noInitiation() {
        consent.setInstructedAmount(null);
        consent.setInstructedCurrency(null);
        consent.setCreditorAccountId(null);

        assertThatThrownBy(() -> service.initiate(matching()))
                .hasFieldOrPropertyWithValue("errorCode", "CONSENT_HAS_NO_INITIATION");
        verifyNoInteractions(paymentService);
    }

    @Test
    @DisplayName("refused transfer: the error stands and the consent stays AUTHORISED (rolled back, usable again)")
    void refusedTransfer() {
        when(paymentService.transfer(any(), anyString()))
                .thenThrow(CbaException.badRequest("INSUFFICIENT_FUNDS", "Insufficient available balance"));

        assertThatThrownBy(() -> service.initiate(matching()))
                .hasFieldOrPropertyWithValue("errorCode", "INSUFFICIENT_FUNDS");
        assertThat(consent.getStatus()).isEqualTo(ConsentStatus.AUTHORISED);
        verify(consentRepository, never()).save(any());
    }

    @Test
    @DisplayName("already CONSUMED, same request again: the original payment back (replay), no second transfer")
    void replay() {
        consent.setStatus(ConsentStatus.CONSUMED);
        consent.setPaymentId(paymentId);
        PaymentResponse p = payment();
        when(paymentService.getPayment(paymentId)).thenReturn(p);

        PaymentInitiationService.Initiated result = service.initiate(matching());

        assertThat(result.replay()).isTrue();
        assertThat(result.payment()).isSameAs(p);
        verify(paymentService, never()).transfer(any(), anyString());
    }

    @Test
    @DisplayName("already CONSUMED, a different payment: 403 CONSENT_CONSUMED")
    void consumedDifferent() {
        consent.setStatus(ConsentStatus.CONSUMED);
        consent.setPaymentId(paymentId);

        assertThatThrownBy(() -> service.initiate(request("30.00", "USD", creditor, "INV-1")))
                .hasFieldOrPropertyWithValue("errorCode", "CONSENT_CONSUMED");
        verify(paymentService, never()).transfer(any(), anyString());
    }
}
