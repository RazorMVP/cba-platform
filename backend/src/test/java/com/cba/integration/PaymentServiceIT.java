package com.cba.integration;

import com.cba.account.Account;
import com.cba.account.AccountRepository;
import com.cba.payment.PaymentService;
import com.cba.payment.dto.PaymentResponse;
import com.cba.payment.dto.TransferRequest;
import com.cba.payment.PaymentStatus;
import com.cba.common.exception.CbaException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("PaymentService — double-entry ledger integration tests")
class PaymentServiceIT extends AbstractIntegrationTest {

    @Autowired PaymentService paymentService;
    @Autowired AccountRepository accountRepository;

    // Demo data UUIDs from V2__demo_data.sql
    static final UUID JOHN_SAVINGS  = UUID.fromString("40000000-0000-0000-0000-000000000001");
    static final UUID JANE_SAVINGS  = UUID.fromString("40000000-0000-0000-0000-000000000003");

    @Test
    @DisplayName("transfer debits source and credits destination atomically")
    void transfer_doubleEntry_balancesAreConsistent() {
        Account srcBefore  = accountRepository.findById(JOHN_SAVINGS).orElseThrow();
        Account dstBefore  = accountRepository.findById(JANE_SAVINGS).orElseThrow();
        BigDecimal srcBal  = srcBefore.getBalance();
        BigDecimal dstBal  = dstBefore.getBalance();
        BigDecimal amount  = new BigDecimal("100.00");

        TransferRequest req = new TransferRequest(JOHN_SAVINGS, JANE_SAVINGS, amount, "Test transfer", null);
        PaymentResponse payment = paymentService.transfer(req, "test-user");

        assertThat(payment.status()).isEqualTo(PaymentStatus.COMPLETED);

        Account srcAfter = accountRepository.findById(JOHN_SAVINGS).orElseThrow();
        Account dstAfter = accountRepository.findById(JANE_SAVINGS).orElseThrow();

        assertThat(srcAfter.getBalance()).isEqualByComparingTo(srcBal.subtract(amount));
        assertThat(dstAfter.getBalance()).isEqualByComparingTo(dstBal.add(amount));
    }

    @Test
    @DisplayName("transfer to same account throws SAME_ACCOUNT_TRANSFER")
    void transfer_sameAccount_throws() {
        assertThatThrownBy(() -> paymentService.transfer(
            new TransferRequest(JOHN_SAVINGS, JOHN_SAVINGS, BigDecimal.TEN, "Self", null),
            "test"
        ))
        .isInstanceOf(CbaException.class)
        .hasMessageContaining("differ");
    }

    @Test
    @DisplayName("transfer of amount exceeding balance throws INSUFFICIENT_BALANCE")
    void transfer_insufficientBalance_throws() {
        assertThatThrownBy(() -> paymentService.transfer(
            new TransferRequest(JOHN_SAVINGS, JANE_SAVINGS, new BigDecimal("9999999.00"), "Overdraft", null),
            "test"
        ))
        .isInstanceOf(CbaException.class)
        .hasMessageContaining("Insufficient balance");
    }
}
