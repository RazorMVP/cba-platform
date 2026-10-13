package com.cba.integration;

import com.cba.account.AccountController;
import com.cba.common.exception.CbaException;
import com.cba.config.CustomerAccess;
import com.cba.customer.CustomerController;
import com.cba.notification.InAppNotificationController;
import com.cba.payment.PaymentController;
import com.cba.payment.dto.TransferRequest;
import com.cba.user.TwoFactorController;
import com.cba.user.TwoFactorService;
import com.cba.user.TwoFactorToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Customer ownership through the real method-security proxies and SQL: a CUSTOMER reaches
 * only their own data; staff reach everything. Controllers are called as beans with the
 * security context set by hand (the HTTP dev auth bypass makes every caller staff).
 */
@DisplayName("Customer ownership — @PreAuthorize + CustomerAccess against a real database")
class CustomerOwnershipIT extends AbstractIntegrationTest {

    static final UUID DEMO_CUSTOMER = UUID.fromString("30000000-0000-0000-0000-000000000001");
    static final UUID SAVINGS_PRODUCT = UUID.fromString("10000000-0000-0000-0000-000000000001");
    static final UUID ADMIN_USER = UUID.fromString("20000000-0000-0000-0000-000000000001");
    static final String SUBJECT = "kc-ownership-it";

    @Autowired AccountController accountController;
    @Autowired CustomerController customerController;
    @Autowired PaymentController paymentController;
    @Autowired InAppNotificationController notificationController;
    @Autowired TwoFactorController twoFactorController;
    @Autowired TwoFactorService twoFactorService;
    @Autowired CustomerAccess customerAccess;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    String previousKeycloakId;
    UUID otherCustomer;
    UUID myAccount;
    UUID theirAccount;

    @BeforeEach
    void setUp() {
        previousKeycloakId = jdbc.queryForObject("SELECT keycloak_id FROM customers WHERE id = ?", String.class, DEMO_CUSTOMER);
        jdbc.update("UPDATE customers SET keycloak_id = ? WHERE id = ?", SUBJECT, DEMO_CUSTOMER);
        otherCustomer = jdbc.queryForObject("SELECT id FROM customers WHERE id <> ? ORDER BY id LIMIT 1", UUID.class, DEMO_CUSTOMER);
        myAccount = account(DEMO_CUSTOMER, "OWN");
        theirAccount = account(otherCustomer, "OTH");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        jdbc.update("UPDATE customers SET keycloak_id = ? WHERE id = ?", previousKeycloakId, DEMO_CUSTOMER);
    }

    @Test
    @DisplayName("a customer reads their own customer record and account, not anyone else's")
    void customer_readsOnlyOwnData() {
        actAsCustomer(SUBJECT);

        assertThat(customerController.getCustomer(DEMO_CUSTOMER).getStatusCode().value()).isEqualTo(200);
        assertThat(accountController.getAccount(myAccount).getStatusCode().value()).isEqualTo(200);

        assertThatThrownBy(() -> customerController.getCustomer(otherCustomer)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> accountController.getAccount(theirAccount)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> accountController.getAccount(UUID.randomUUID())).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("a customer cannot move money out of someone else's account")
    void customer_cannotTransferFromAnotherAccount() {
        actAsCustomer(SUBJECT);
        BigDecimal before = balance(theirAccount);

        assertThatThrownBy(() -> paymentController.transfer(
                new TransferRequest(theirAccount, myAccount, new BigDecimal("10.00"), "it", null), jwt(SUBJECT)))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(balance(theirAccount)).isEqualByComparingTo(before);
    }

    @Test
    @DisplayName("the back-office notification inbox is staff only")
    void customer_noStaffInbox() {
        actAsCustomer(SUBJECT);
        assertThatThrownBy(() -> notificationController.inbox(PageRequest.of(0, 5)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("staff still reach every customer's data")
    void staff_readAnyone() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "teller", "n/a", List.of(new SimpleGrantedAuthority("ROLE_TELLER"))));

        assertThat(accountController.getAccount(theirAccount).getStatusCode().value()).isEqualTo(200);
        assertThat(customerController.getCustomer(otherCustomer).getStatusCode().value()).isEqualTo(200);
    }

    @Test
    @DisplayName("ownership SQL runs against the real schema for every resource type")
    void ownershipQueries_realSchema() {
        actAsCustomer(SUBJECT);
        UUID unknown = UUID.randomUUID();

        assertThat(customerAccess.callerCustomerId()).contains(DEMO_CUSTOMER);
        assertThat(customerAccess.isAccountOwner(myAccount)).isTrue();
        assertThat(customerAccess.isPaymentParty(unknown)).isFalse();
        assertThat(customerAccess.isFixedDepositOwner(unknown)).isFalse();
        assertThat(customerAccess.isRecurringDepositOwner(unknown)).isFalse();
        assertThat(customerAccess.isShareAccountOwner(unknown)).isFalse();
    }

    @Test
    @DisplayName("a 2FA code can only be verified by the user it was issued to")
    void twoFactor_boundToCaller() {
        TwoFactorToken token = twoFactorService.generateToken(
                new TwoFactorService.GenerateTokenRequest(ADMIN_USER, TwoFactorToken.DeliveryMethod.EMAIL));
        String adminSubject = jdbc.queryForObject("SELECT keycloak_id FROM platform_users WHERE id = ?", String.class, ADMIN_USER);

        actAsStaff("teller-keycloak-id");
        assertThatThrownBy(() -> twoFactorController.verify(new TwoFactorService.VerifyTokenRequest(token.getToken())))
                .isInstanceOf(CbaException.class).hasMessageContaining("not found");

        actAsStaff(adminSubject);
        TwoFactorToken verified = twoFactorController.verify(new TwoFactorService.VerifyTokenRequest(token.getToken())).data();
        assertThat(verified.isVerified()).isTrue();
    }

    @Test
    @DisplayName("a 2FA token serialises with userId, not the lazy staff user")
    void twoFactor_serialises() throws Exception {
        TwoFactorToken token = twoFactorService.generateToken(
                new TwoFactorService.GenerateTokenRequest(ADMIN_USER, TwoFactorToken.DeliveryMethod.EMAIL));

        String json = objectMapper.writeValueAsString(token);

        assertThat(json).contains("\"userId\":\"" + ADMIN_USER + "\"").doesNotContain("\"user\":");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UUID account(UUID customer, String tag) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO accounts (id, account_number, customer_id, product_id, account_type,
                                      status, balance, currency_code, opened_date, created_by)
                VALUES (?, ?, ?, ?, 'SAVINGS', 'ACTIVE', 500.00, 'USD', DATE '2025-01-01', 'ownership-it')""",
                id, "OWN-" + tag + "-" + id.toString().substring(0, 6), customer, SAVINGS_PRODUCT);
        return id;
    }

    private BigDecimal balance(UUID account) {
        return jdbc.queryForObject("SELECT balance FROM accounts WHERE id = ?", BigDecimal.class, account);
    }

    private static Jwt jwt(String subject) {
        return Jwt.withTokenValue("token").header("alg", "none").subject(subject)
                .claim("preferred_username", "it-user").build();
    }

    private static void actAsCustomer(String subject) {
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt(subject), List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
    }

    private static void actAsStaff(String subject) {
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt(subject), List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }
}
