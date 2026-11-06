package com.cba.openbanking;

import com.cba.account.AccountBalanceChangedEvent;
import com.cba.account.AccountRepository;
import com.cba.partner.PartnerWebhookDeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.UUID;

/**
 * First half of {@code ACCOUNT.BALANCE_UPDATED}: after a balance change commits, mark the account
 * pending for every partner organisation holding a usable {@code balances_read} consent of its
 * customer. Nothing is sent here — {@link BalanceNotificationDispatcher} sends, at most once per window,
 * so a busy account doesn't flood a TPP. Off the request thread ({@code @Async}); failures are logged,
 * never thrown back into the payment that changed the balance.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountBalancePartnerNotifier {

    static final String UPSERT = """
            INSERT INTO partner_balance_notifications (org_id, account_id, consent_id, pending_since)
            VALUES (?, ?, ?, now())
            ON CONFLICT (org_id, account_id) DO UPDATE
               SET consent_id    = EXCLUDED.consent_id,
                   pending_since = COALESCE(partner_balance_notifications.pending_since, EXCLUDED.pending_since)""";

    private final AccountRepository accountRepository;
    private final ConsentRepository consentRepository;
    private final JdbcTemplate jdbc;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onBalanceChanged(AccountBalanceChangedEvent event) {
        try {
            UUID customerId = accountRepository.findById(event.accountId())
                    .map(a -> a.getCustomer() == null ? null : a.getCustomer().getId())
                    .orElse(null);
            if (customerId == null) {
                return;
            }
            for (OpenBankingConsent consent : consentRepository.findUsableByCustomerAndScope(
                    customerId, ConsentScope.BALANCES_READ.value(), Instant.now())) {
                UUID org = PartnerWebhookDeliveryService.parseOrg(consent.getTppClientId());
                if (org != null) {
                    jdbc.update(UPSERT, org, event.accountId(), consent.getConsentId());
                }
            }
        } catch (RuntimeException e) {
            log.warn("Could not queue ACCOUNT.BALANCE_UPDATED for account {}: {}", event.accountId(), e.toString());
        }
    }
}
