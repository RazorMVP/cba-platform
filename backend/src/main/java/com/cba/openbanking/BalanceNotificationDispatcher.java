package com.cba.openbanking;

import com.cba.account.AccountRepository;
import com.cba.partner.PartnerOrganization;
import com.cba.partner.PartnerOrganizationRepository;
import com.cba.partner.PartnerWebhookDeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Second half of {@code ACCOUNT.BALANCE_UPDATED}: sends the pending notifications, at most one per
 * partner organisation and account per window ({@code app.partner.balance-webhook.window-seconds},
 * default 60). A change during the window keeps the row pending, so it is sent when the window ends —
 * the last change is never lost (trailing edge). Each send first re-checks the consent: revoked,
 * expired or no longer covering the account → nothing is sent and the row is dropped.
 *
 * <p>The payload is a signal only — {@code {consentId, accountId, updatedAt}} — the TPP reads the
 * balance through AISP, under its consent (decision D2a). Rows are claimed with
 * {@code FOR UPDATE SKIP LOCKED} inside one transaction (a {@link TransactionOperations}, not
 * {@code @Transactional}: the scheduler calls {@link #dispatch} on {@code this}, which would bypass the
 * proxy and leave the locks in auto-commit), so several backend instances never send the same one.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BalanceNotificationDispatcher {

    static final String DUE = """
            SELECT org_id, account_id, consent_id, pending_since
              FROM partner_balance_notifications
             WHERE pending_since IS NOT NULL
               AND (last_sent_at IS NULL OR last_sent_at <= ?)
             ORDER BY pending_since
             LIMIT 500
             FOR UPDATE SKIP LOCKED""";

    private final JdbcTemplate jdbc;
    private final ConsentRepository consentRepository;
    private final AccountRepository accountRepository;
    private final PartnerWebhookDeliveryService webhookDelivery;
    private final TransactionOperations transactions;
    private final PartnerOrganizationRepository orgRepository;

    @Value("${app.partner.balance-webhook.window-seconds:60}")
    private long windowSeconds;

    @Scheduled(fixedDelayString = "${app.partner.balance-webhook.dispatch-ms:15000}",
               initialDelayString = "${app.partner.balance-webhook.dispatch-ms:15000}")
    public void scheduled() {
        try {
            dispatch(Instant.now());
        } catch (RuntimeException e) {
            log.warn("ACCOUNT.BALANCE_UPDATED dispatch failed: {}", e.toString());
        }
    }

    /** @return the number of events sent */
    public int dispatch(Instant now) {
        Integer sent = transactions.execute(status -> dispatchLocked(now));
        return sent == null ? 0 : sent;
    }

    private int dispatchLocked(Instant now) {
        List<Map<String, Object>> due = jdbc.queryForList(DUE, Timestamp.from(now.minusSeconds(windowSeconds)));
        int sent = 0;
        for (Map<String, Object> row : due) {
            UUID org = (UUID) row.get("org_id");
            UUID accountId = (UUID) row.get("account_id");
            String consentId = (String) row.get("consent_id");
            if (!stillCovered(org, accountId, consentId, now)) {
                jdbc.update("DELETE FROM partner_balance_notifications WHERE org_id = ? AND account_id = ?",
                        org, accountId);
                continue;
            }
            Instant changedAt = ((Timestamp) row.get("pending_since")).toInstant();
            webhookDelivery.publishEvent(org, "ACCOUNT.BALANCE_UPDATED", Map.of(
                    "consentId", consentId,
                    "accountId", accountId.toString(),
                    "updatedAt", changedAt.toString()));
            jdbc.update("""
                    UPDATE partner_balance_notifications
                       SET last_sent_at = ?, pending_since = NULL
                     WHERE org_id = ? AND account_id = ?""", Timestamp.from(now), org, accountId);
            sent++;
        }
        return sent;
    }

    /**
     * The org is not suspended, and the consent is the org's, AUTHORISED, unexpired, grants
     * balances_read, and the account is still its customer's.
     */
    private boolean stillCovered(UUID org, UUID accountId, String consentId, Instant now) {
        if (orgRepository.findById(org).map(PartnerOrganization::isSuspended).orElse(true)) {
            return false;
        }
        return consentRepository.findByConsentId(consentId)
                .filter(c -> org.toString().equals(c.getTppClientId()))
                .filter(c -> c.getStatus() == ConsentStatus.AUTHORISED)
                .filter(c -> c.getExpiryDate() == null || c.getExpiryDate().isAfter(now))
                .filter(c -> c.getScopes() != null && c.getScopes().contains(ConsentScope.BALANCES_READ.value()))
                .map(c -> accountRepository.findById(accountId)
                        .map(a -> a.getCustomer() != null && c.getCustomer().getId().equals(a.getCustomer().getId()))
                        .orElse(false))
                .orElse(false);
    }
}
