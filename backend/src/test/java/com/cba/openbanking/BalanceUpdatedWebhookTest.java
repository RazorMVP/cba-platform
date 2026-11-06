package com.cba.openbanking;

import com.cba.account.Account;
import com.cba.account.AccountBalanceChangedEvent;
import com.cba.account.AccountRepository;
import com.cba.customer.Customer;
import com.cba.partner.PartnerWebhookDeliveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** {@code ACCOUNT.BALANCE_UPDATED}: queue after commit, send at most once per window (backlog 3). */
@DisplayName("ACCOUNT.BALANCE_UPDATED — notifier and dispatcher")
class BalanceUpdatedWebhookTest {

    static final UUID ORG = ConsentServiceTest.ORG;

    AccountRepository accountRepository = mock(AccountRepository.class);
    ConsentRepository consentRepository = mock(ConsentRepository.class);
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    PartnerWebhookDeliveryService webhookDelivery = mock(PartnerWebhookDeliveryService.class);

    UUID customerId = UUID.randomUUID();
    UUID accountId = UUID.randomUUID();
    OpenBankingConsent consent;

    @BeforeEach
    void setUp() {
        Customer c = new Customer();
        c.setId(customerId);
        Account a = new Account();
        a.setId(accountId);
        a.setCustomer(c);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(a));

        consent = new OpenBankingConsent();
        consent.setConsentId("ob-bal0000000000001");
        consent.setCustomer(c);
        consent.setTppClientId(ORG.toString());
        consent.setScopes(List.of("balances_read"));
        consent.setStatus(ConsentStatus.AUTHORISED);
    }

    @Nested
    @DisplayName("AccountBalancePartnerNotifier")
    class Notifier {

        AccountBalancePartnerNotifier notifier() {
            return new AccountBalancePartnerNotifier(accountRepository, consentRepository, jdbc);
        }

        @Test
        @DisplayName("a usable balances_read consent of the customer: the (org, account) row is marked pending")
        void queues() {
            when(consentRepository.findUsableByCustomerAndScope(eq(customerId), eq("balances_read"), any()))
                    .thenReturn(List.of(consent));

            notifier().onBalanceChanged(new AccountBalanceChangedEvent(accountId));

            verify(jdbc).update(AccountBalancePartnerNotifier.UPSERT, ORG, accountId, consent.getConsentId());
            verifyNoInteractions(webhookDelivery);
        }

        @Test
        @DisplayName("a consent whose TPP isn't a partner organisation: nothing queued")
        void notPartner() {
            consent.setTppClientId("legacy-client");
            when(consentRepository.findUsableByCustomerAndScope(any(), anyString(), any())).thenReturn(List.of(consent));

            notifier().onBalanceChanged(new AccountBalanceChangedEvent(accountId));

            verifyNoInteractions(jdbc);
        }

        @Test
        @DisplayName("a failure is logged, never thrown back into the payment that changed the balance")
        void swallowsFailure() {
            when(consentRepository.findUsableByCustomerAndScope(any(), anyString(), any()))
                    .thenThrow(new IllegalStateException("db down"));

            notifier().onBalanceChanged(new AccountBalanceChangedEvent(accountId));

            verifyNoInteractions(jdbc);
        }
    }

    @Nested
    @DisplayName("BalanceNotificationDispatcher")
    class Dispatcher {

        Instant now = Instant.parse("2026-11-06T12:00:00Z");
        Instant changedAt = now.minusSeconds(20);

        BalanceNotificationDispatcher dispatcher() {
            BalanceNotificationDispatcher d = new BalanceNotificationDispatcher(jdbc, consentRepository,
                    accountRepository, webhookDelivery,
                    org.springframework.transaction.support.TransactionOperations.withoutTransaction());
            ReflectionTestUtils.setField(d, "windowSeconds", 60L);
            return d;
        }

        @BeforeEach
        void due() {
            when(jdbc.queryForList(eq(BalanceNotificationDispatcher.DUE), any(Object[].class))).thenReturn(List.of(
                    Map.of("org_id", ORG, "account_id", accountId, "consent_id", consent.getConsentId(),
                            "pending_since", Timestamp.from(changedAt))));
            when(consentRepository.findByConsentId(consent.getConsentId())).thenReturn(Optional.of(consent));
        }

        @Test
        @DisplayName("due rows are claimed with the window cutoff; covered → one signal-only event, row marked sent")
        void sends() {
            int sent = dispatcher().dispatch(now);

            assertThat(sent).isEqualTo(1);
            verify(jdbc).queryForList(BalanceNotificationDispatcher.DUE, Timestamp.from(now.minusSeconds(60)));
            verify(webhookDelivery).publishEvent(ORG, "ACCOUNT.BALANCE_UPDATED", Map.of(
                    "consentId", consent.getConsentId(),
                    "accountId", accountId.toString(),
                    "updatedAt", changedAt.toString()));
            verify(jdbc).update(contains("SET last_sent_at"), eq(Timestamp.from(now)), eq(ORG), eq(accountId));
        }

        @Test
        @DisplayName("consent revoked since the change: nothing sent, the row dropped")
        void revoked() {
            consent.setStatus(ConsentStatus.REVOKED);

            assertThat(dispatcher().dispatch(now)).isZero();

            verifyNoInteractions(webhookDelivery);
            verify(jdbc).update(contains("DELETE FROM partner_balance_notifications"), eq(ORG), eq(accountId));
        }

        @Test
        @DisplayName("consent expired: nothing sent")
        void expired() {
            consent.setExpiryDate(now.minusSeconds(1));

            assertThat(dispatcher().dispatch(now)).isZero();
            verifyNoInteractions(webhookDelivery);
        }
    }
}
