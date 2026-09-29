package com.cba.account;

import com.cba.accounting.FinancialActivityAccount.FinancialActivity;
import com.cba.accounting.GlAccount;
import com.cba.accounting.GlAccountingService;
import com.cba.accounting.GlAccountingService.JournalLine;
import com.cba.accounting.JournalEntry;
import com.cba.common.exception.CbaException;
import com.cba.currency.ExchangeRateService;
import com.cba.product.DepositProduct;
import com.cba.system.GlobalConfigurationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Posts deposit-account balance changes to the general ledger, in the same transaction
 * as the balance change. Every caller passes the balance <em>before</em> the change; the
 * account already holds the balance after it.
 *
 * <p>GL accounts are resolved from the deposit product's own GL links first, then from
 * the financial-activity mapping. When neither exists the transaction is rejected
 * ({@code ACTIVITY_NOT_MAPPED}), so no balance ever moves without a journal.
 *
 * <p>A credit balance is a deposit (liability, savings control). A debit balance is an
 * overdraft, which is a loan to the customer (asset, overdraft portfolio), not a negative
 * deposit (FFIEC Call Report RC-E; IAS 32 §42). A change that crosses zero posts to both.
 */
@Component
@RequiredArgsConstructor
public class AccountGlPosting {

    static final String FUNCTIONAL_CURRENCY = "functional-currency";

    private final GlAccountingService gl;
    private final GlobalConfigurationRepository globalConfigRepository;
    private final ExchangeRateService exchangeRateService;

    /** Teller cash in or out: the counter-entry is Cash at Teller. */
    public String postCashMovement(Account account, BigDecimal balanceBefore, LocalDate date,
                                   String description, String reference) {
        List<JournalLine> lines = new ArrayList<>(balanceChangeLines(account, balanceBefore));
        lines.add(counterLine(gl.activityAccount(FinancialActivity.ASSET_CASH_AT_TELLER),
                account.getBalance().subtract(balanceBefore), account.getCurrencyCode()));
        return gl.postJournal(lines, date, description, JournalEntry.EntityType.ACCOUNT, account.getId(), reference);
    }

    /** Interest credited to the customer: an interest expense of the bank. */
    public String postInterestCredit(Account account, BigDecimal balanceBefore, LocalDate date,
                                     String description, String reference) {
        List<JournalLine> lines = new ArrayList<>(balanceChangeLines(account, balanceBefore));
        lines.add(counterLine(interestOnSavings(account.getProduct()),
                account.getBalance().subtract(balanceBefore), account.getCurrencyCode()));
        return gl.postJournal(lines, date, description, JournalEntry.EntityType.ACCOUNT, account.getId(), reference);
    }

    /**
     * Account-to-account movement (transfer, standing order, reversal). Same currency: the
     * two balance changes offset each other. Cross-currency: each foreign leg is balanced
     * through the FX position account in its own currency, with the functional-currency
     * equivalent at the spot rate (IAS 21 §21); any functional-currency residual is a
     * realised exchange difference and goes to profit or loss (IAS 21 §28).
     */
    public String postTransfer(Account source, BigDecimal sourceBefore,
                               Account destination, BigDecimal destinationBefore,
                               LocalDate date, String description, String reference, UUID paymentId) {
        List<JournalLine> lines = new ArrayList<>();
        lines.addAll(balanceChangeLines(source, sourceBefore));
        lines.addAll(balanceChangeLines(destination, destinationBefore));

        String srcCcy = source.getCurrencyCode();
        String dstCcy = destination.getCurrencyCode();
        if (!srcCcy.equalsIgnoreCase(dstCcy)) {
            String functional = functionalCurrency();
            // Signed movement of each customer balance: positive = the bank owes more.
            addFxLeg(lines, srcCcy, source.getBalance().subtract(sourceBefore), functional);
            addFxLeg(lines, dstCcy, destination.getBalance().subtract(destinationBefore), functional);
            addFunctionalResidual(lines, functional);
        }
        return gl.postJournal(lines, date, description, JournalEntry.EntityType.PAYMENT, paymentId, reference);
    }

    /** Teller count difference at session close: over is a gain, short a loss. */
    public String postCashCountDifference(BigDecimal difference, String currency, LocalDate date,
                                          String description, UUID sessionId) {
        GlAccount cash = gl.activityAccount(FinancialActivity.ASSET_CASH_AT_TELLER);
        GlAccount overShort = gl.activityAccount(FinancialActivity.EXPENSE_CASH_OVER_SHORT);
        List<JournalLine> lines = difference.signum() > 0
                ? List.of(JournalLine.debit(cash, difference, currency), JournalLine.credit(overShort, difference, currency))
                : List.of(JournalLine.debit(overShort, difference.negate(), currency), JournalLine.credit(cash, difference.negate(), currency));
        return gl.postJournal(lines, date, description, JournalEntry.EntityType.TELLER_CASH, sessionId, null);
    }

    // ── Lines ────────────────────────────────────────────────────────────────

    /** Lines that move the account's GL balance from {@code before} to its current balance. */
    List<JournalLine> balanceChangeLines(Account account, BigDecimal before) {
        BigDecimal after = account.getBalance();
        String ccy = account.getCurrencyCode();
        List<JournalLine> lines = new ArrayList<>(2);

        BigDecimal depositDelta = positivePart(after).subtract(positivePart(before));
        if (depositDelta.signum() > 0) {
            lines.add(JournalLine.credit(savingsControl(account.getProduct()), depositDelta, ccy));
        } else if (depositDelta.signum() < 0) {
            lines.add(JournalLine.debit(savingsControl(account.getProduct()), depositDelta.negate(), ccy));
        }

        BigDecimal overdraftDelta = positivePart(after.negate()).subtract(positivePart(before.negate()));
        if (overdraftDelta.signum() > 0) {
            lines.add(JournalLine.debit(overdraftPortfolio(account.getProduct()), overdraftDelta, ccy));
        } else if (overdraftDelta.signum() < 0) {
            lines.add(JournalLine.credit(overdraftPortfolio(account.getProduct()), overdraftDelta.negate(), ccy));
        }
        return lines;
    }

    /** The line that balances a customer balance change of {@code increase}. */
    private static JournalLine counterLine(GlAccount account, BigDecimal increase, String currency) {
        return increase.signum() >= 0
                ? JournalLine.debit(account, increase, currency)
                : JournalLine.credit(account, increase.negate(), currency);
    }

    /**
     * Balances one foreign-currency leg. A customer balance that rose (the bank owes more
     * in that currency) is balanced by a debit to the position; its functional-currency
     * equivalent goes the other way. Functional-currency legs need no position line.
     */
    private void addFxLeg(List<JournalLine> lines, String currency, BigDecimal increase, String functional) {
        if (currency.equalsIgnoreCase(functional) || increase.signum() == 0) return;
        BigDecimal rate = exchangeRateService.getRate(currency, functional).getRate();
        BigDecimal equivalent = increase.abs().multiply(rate).setScale(4, RoundingMode.HALF_UP);
        GlAccount position = gl.activityAccount(FinancialActivity.ASSET_FX_POSITION);
        GlAccount positionEquivalent = gl.activityAccount(FinancialActivity.ASSET_FX_POSITION_EQUIVALENT);
        if (increase.signum() > 0) {
            lines.add(JournalLine.debit(position, increase, currency));
            lines.add(JournalLine.credit(positionEquivalent, equivalent, functional));
        } else {
            lines.add(JournalLine.credit(position, increase.negate(), currency));
            lines.add(JournalLine.debit(positionEquivalent, equivalent, functional));
        }
    }

    /** Posts any functional-currency imbalance (rate and rounding differences) to FX gain/loss. */
    private void addFunctionalResidual(List<JournalLine> lines, String functional) {
        BigDecimal net = lines.stream()
                .filter(l -> l.currencyCode().equalsIgnoreCase(functional))
                .map(l -> l.side() == JournalEntry.EntryType.DEBIT ? l.amount() : l.amount().negate())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (net.signum() == 0) return;
        GlAccount fxGainLoss = gl.activityAccount(FinancialActivity.INCOME_FX_GAIN_LOSS);
        lines.add(net.signum() > 0
                ? JournalLine.credit(fxGainLoss, net, functional)
                : JournalLine.debit(fxGainLoss, net.negate(), functional));
    }

    // ── Account resolution: product link first, then activity mapping ────────

    private GlAccount savingsControl(DepositProduct p) {
        return p != null && p.getSavingsControlAccount() != null
                ? p.getSavingsControlAccount() : gl.activityAccount(FinancialActivity.LIABILITY_SAVINGS_CONTROL);
    }

    private GlAccount overdraftPortfolio(DepositProduct p) {
        return p != null && p.getOverdraftPortfolioControlAccount() != null
                ? p.getOverdraftPortfolioControlAccount() : gl.activityAccount(FinancialActivity.ASSET_OVERDRAFT_PORTFOLIO);
    }

    private GlAccount interestOnSavings(DepositProduct p) {
        return p != null && p.getInterestOnSavingsAccount() != null
                ? p.getInterestOnSavingsAccount() : gl.activityAccount(FinancialActivity.EXPENSE_INTEREST_ON_SAVINGS);
    }

    /** The ledger's single functional currency (IAS 21 §17); rejects when not configured. */
    String functionalCurrency() {
        return globalConfigRepository.findByName(FUNCTIONAL_CURRENCY)
                .filter(c -> c.isEnabled() && c.getStringValue() != null && !c.getStringValue().isBlank())
                .map(c -> c.getStringValue().trim().toUpperCase(java.util.Locale.ROOT))
                .orElseThrow(() -> CbaException.badRequest("FUNCTIONAL_CURRENCY_NOT_CONFIGURED",
                        "Global configuration '" + FUNCTIONAL_CURRENCY + "' must be set before cross-currency postings"));
    }

    private static BigDecimal positivePart(BigDecimal v) {
        return v.signum() > 0 ? v : BigDecimal.ZERO;
    }
}
