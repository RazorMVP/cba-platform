package com.cba.config;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Ownership checks for endpoints open to the CUSTOMER role, used from {@code @PreAuthorize}:
 *
 * <pre>
 * &#64;PreAuthorize("hasAnyRole('ADMIN','TELLER') or (hasRole('CUSTOMER') and &#64;customerAccess.isAccountOwner(#id))")
 * </pre>
 *
 * The caller is the customer whose {@code keycloak_id} is the JWT {@code sub} (the same
 * link self-service uses). Every check is false — so the request is refused — when the
 * caller is not a JWT-authenticated customer, the resource doesn't exist, or it belongs
 * to someone else; a refusal looks the same either way, so ids can't be probed. Staff are
 * let through by the {@code hasAnyRole('ADMIN','TELLER')} half of the expression, not here.
 */
@Component("customerAccess")
@RequiredArgsConstructor
public class CustomerAccess {

    private final JdbcTemplate jdbc;

    /** The caller is this customer. */
    public boolean isCustomer(UUID customerId) {
        return customerId != null && callerCustomerId().map(customerId::equals).orElse(false);
    }

    /** The account (savings/current) belongs to the caller. */
    public boolean isAccountOwner(UUID accountId) {
        return ownerIs("SELECT customer_id FROM accounts WHERE id = ?", accountId);
    }

    /** The payment's source or destination account belongs to the caller. */
    public boolean isPaymentParty(UUID paymentId) {
        if (paymentId == null) return false;
        Optional<UUID> caller = callerCustomerId();
        if (caller.isEmpty()) return false;
        List<UUID> parties = jdbc.queryForList("""
                SELECT a.customer_id FROM payments p
                JOIN accounts a ON a.id IN (p.source_account_id, p.destination_account_id)
                WHERE p.id = ?""", UUID.class, paymentId);
        return parties.contains(caller.get());
    }

    public boolean isFixedDepositOwner(UUID id) {
        return ownerIs("SELECT customer_id FROM fixed_deposit_accounts WHERE id = ?", id);
    }

    public boolean isRecurringDepositOwner(UUID id) {
        return ownerIs("SELECT customer_id FROM recurring_deposit_accounts WHERE id = ?", id);
    }

    public boolean isShareAccountOwner(UUID id) {
        return ownerIs("SELECT customer_id FROM share_accounts WHERE id = ?", id);
    }

    /** The customer linked to the caller's JWT subject, if any. */
    public Optional<UUID> callerCustomerId() {
        String subject = callerSubject();
        if (subject == null) return Optional.empty();
        return jdbc.queryForList("SELECT id FROM customers WHERE keycloak_id = ?", UUID.class, subject)
                .stream().findFirst();
    }

    /** The caller's JWT subject; null when the caller is not authenticated with a JWT. */
    public static String callerSubject() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof Jwt jwt ? jwt.getSubject() : null;
    }

    private boolean ownerIs(String ownerSql, UUID resourceId) {
        if (resourceId == null) return false;
        Optional<UUID> caller = callerCustomerId();
        if (caller.isEmpty()) return false;
        return jdbc.queryForList(ownerSql, UUID.class, resourceId).stream().findFirst()
                .map(caller.get()::equals).orElse(false);
    }
}
