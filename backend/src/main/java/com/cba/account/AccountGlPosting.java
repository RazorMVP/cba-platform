package com.cba.account;

import com.cba.accounting.FinancialActivityAccount.FinancialActivity;
import com.cba.accounting.FunctionalCurrency;
import com.cba.accounting.GlAccount;
import com.cba.accounting.GlAccountingService;
import com.cba.accounting.GlAccountingService.JournalLine;
import com.cba.accounting.JournalEntry;
import com.cba.currency.ExchangeRateService;
import com.cba.product.DepositProduct;
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

    private final GlAccountingService gl;
    private final FunctionalCurrency functionalCurrency;
    private final ExchangeRateService exchangeRateService;

    /** Teller cash in or out: the counter-entry is Cash at Teller. */
    public String postCashMovement(Account account, BigDecimal balanceBefore, LocalDate date,
                                   String description, String reference) {
        List<JournalLine> lines = new ArrayList<>(balanceChangeLines(account, balanceBefore));
        lines.add(counterLine(gl.activityAccount(FinancialActivity.ASSET_CASH_AT_TELLER),
                account.getBalance().subtract(balanceBefore), account.getCurrencyCode()));
        return gl.postJournal(lines, date, description, JournalEntry.EntityType.ACCOUNT, account.getId(), reference);
    }

    /**
     * Card clearing: the counter-entry is the card scheme settlement payable (GL 2300). A debit
     * (purchase cleared, ATM cash) moves part of the deposit to what the bank owes the scheme
     * (IFRS 9 §3.3.1; IAS 32 §11); a refund moves it back. The authorization hold before it
     * posted nothing.
     */
    public String postCardSettlement(Account account, BigDecimal balanceBefore, LocalDate date,
                                     String description, String reference) {
        return postCardSettlement(account, balanceBefore, CardCharges.NONE, false, date, description, reference);
    }

    /**
     * Card clearing with its interchange and scheme fees, in one journal (backlog 15b). 2300 is one
     * net payable per scheme (IAS 32 §42), so interchange earned lowers it and interchange paid and
     * scheme fees raise it; income and expense are shown gross (IFRS 15 B35, B37; IAS 1 §32):
     * <pre>
     *   interchange EARNED:  DR 2300 / CR 4005 Card Interchange Income
     *   interchange PAID:    DR 5007 Card Interchange Expense / CR 2300
     *   scheme fees:         DR 5006 Card Scheme Fees / CR 2300
     * </pre>
     * A refund ({@code reversal}) posts the opposite for the figures it reverses. Income and expense
     * are recorded in the functional currency through the FX position (IAS 21 §21). A fee account is
     * resolved only when its amount is above zero, so a debit without fees needs no new mapping.
     */
    public String postCardSettlement(Account account, BigDecimal balanceBefore, CardCharges charges, boolean reversal,
                                     LocalDate date, String description, String reference) {
        String ccy = account.getCurrencyCode();
        GlAccount payable = gl.activityAccount(FinancialActivity.LIABILITY_CARD_SETTLEMENT);
        List<JournalLine> lines = new ArrayList<>(balanceChangeLines(account, balanceBefore));
        lines.add(counterLine(payable, account.getBalance().subtract(balanceBefore), ccy));

        BigDecimal interchange = charges.interchange();
        boolean earned = charges.direction() == CardInterchangeDirection.EARNED;
        BigDecimal fee = charges.schemeFee();
        // Balance-sheet lines first: addProfitOrLoss needs them all in before it balances the P&L.
        // Lowering 2300 is a debit; raising it is a credit. A reversal flips each line.
        if (interchange.signum() > 0) {
            lines.add(earned != reversal ? JournalLine.debit(payable, interchange, ccy)
                                         : JournalLine.credit(payable, interchange, ccy));
        }
        if (fee.signum() > 0) {
            lines.add(reversal ? JournalLine.debit(payable, fee, ccy) : JournalLine.credit(payable, fee, ccy));
        }
        if (interchange.signum() > 0) {
            if (earned) {
                addProfitOrLoss(lines, gl.activityAccount(FinancialActivity.INCOME_CARD_INTERCHANGE), interchange,
                        reversal ? JournalEntry.EntryType.DEBIT : JournalEntry.EntryType.CREDIT, ccy);
            } else {
                addProfitOrLoss(lines, gl.activityAccount(FinancialActivity.EXPENSE_CARD_INTERCHANGE), interchange,
                        reversal ? JournalEntry.EntryType.CREDIT : JournalEntry.EntryType.DEBIT, ccy);
            }
        }
        if (fee.signum() > 0) {
            addProfitOrLoss(lines, gl.activityAccount(FinancialActivity.EXPENSE_CARD_SCHEME_FEES), fee,
                    reversal ? JournalEntry.EntryType.CREDIT : JournalEntry.EntryType.DEBIT, ccy);
        }
        return gl.postJournal(lines, date, description, JournalEntry.EntityType.CARD, account.getId(), reference);
    }

    /**
     * A card scheme's net settlement (the cash leg, backlog 15b): the bank pays the scheme what 2300
     * holds for it from its settlement-bank account (positive {@code amount}: DR 2300 / CR 1003), or
     * receives it when the scheme owes the bank (negative: DR 1003 / CR 2300). One currency, no FX.
     */
    public String postCardSchemeSettlement(BigDecimal amount, String currency, LocalDate date, String description,
                                           UUID settlementId, String reference) {
        GlAccount payable = gl.activityAccount(FinancialActivity.LIABILITY_CARD_SETTLEMENT);
        GlAccount bank = gl.activityAccount(FinancialActivity.ASSET_CARD_SCHEME_SETTLEMENT);
        BigDecimal abs = amount.abs();
        List<JournalLine> lines = amount.signum() > 0
                ? List.of(JournalLine.debit(payable, abs, currency), JournalLine.credit(bank, abs, currency))
                : List.of(JournalLine.debit(bank, abs, currency), JournalLine.credit(payable, abs, currency));
        return gl.postJournal(lines, date, description, JournalEntry.EntityType.CARD, settlementId, reference);
    }

    /**
     * Interest credited to the customer: an interest expense of the bank, recorded in the
     * functional currency (see {@link #balanceWith}).
     */
    public String postInterestCredit(Account account, BigDecimal balanceBefore, LocalDate date,
                                     String description, String reference) {
        List<JournalLine> lines = new ArrayList<>(balanceChangeLines(account, balanceBefore));
        balanceWith(lines, interestOnSavings(account.getProduct()), account.getCurrencyCode());
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
            String functional = functionalCurrency.get();
            // Signed movement of each customer balance: positive = the bank owes more.
            addFxLeg(lines, srcCcy, source.getBalance().subtract(sourceBefore), functional);
            addFxLeg(lines, dstCcy, destination.getBalance().subtract(destinationBefore), functional);
            balanceResidual(lines, functional, gl.activityAccount(FinancialActivity.INCOME_FX_GAIN_LOSS));
        }
        return gl.postJournal(lines, date, description, JournalEntry.EntityType.PAYMENT, paymentId, reference);
    }

    /**
     * Teller count difference at session close: over is a gain, short a loss, recorded in
     * the functional currency (see {@link #balanceWith}).
     */
    public String postCashCountDifference(BigDecimal difference, String currency, LocalDate date,
                                          String description, UUID sessionId) {
        List<JournalLine> lines = new ArrayList<>(4);
        lines.add(counterLine(gl.activityAccount(FinancialActivity.ASSET_CASH_AT_TELLER), difference, currency));
        balanceWith(lines, gl.activityAccount(FinancialActivity.EXPENSE_CASH_OVER_SHORT), currency);
        return gl.postJournal(lines, date, description, JournalEntry.EntityType.TELLER_CASH, sessionId, null);
    }

    // ── Lines ────────────────────────────────────────────────────────────────

    /**
     * Adds an income or expense line of {@code amount} in {@code currency}, recorded in the
     * functional currency (IAS 21 §21). In a foreign currency the amount first goes through
     * the FX position, exactly as {@link #balanceWith} does. Call it after every
     * balance-sheet line of the journal is in: the functional-currency lines must balance
     * before each call, so the residual it books is this amount's equivalent alone.
     */
    public void addProfitOrLoss(List<JournalLine> lines, GlAccount account, BigDecimal amount,
                                JournalEntry.EntryType side, String currency) {
        if (amount.signum() == 0) return;
        String functional = functionalCurrency.get();
        if (currency.equalsIgnoreCase(functional)) {
            lines.add(new JournalLine(account, side, amount, currency));
            return;
        }
        // An income credit leaves the foreign currency short of credits: the position
        // takes the credit, and its equivalent becomes the functional-currency income.
        BigDecimal increase = side == JournalEntry.EntryType.CREDIT ? amount.negate() : amount;
        addFxLeg(lines, currency, increase, functional);
        balanceResidual(lines, functional, account);
    }

    /** Lines that move the account's GL balance from {@code before} to its current balance. */
    public List<JournalLine> balanceChangeLines(Account account, BigDecimal before) {
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
        // The equivalent line is tagged with the currency it values, so the revaluation
        // job can carry each currency's position at its own closing rate (IAS 21 §23).
        if (increase.signum() > 0) {
            lines.add(JournalLine.debit(position, increase, currency));
            lines.add(JournalLine.credit(positionEquivalent, equivalent, functional).withPosition(currency));
        } else {
            lines.add(JournalLine.credit(position, increase.negate(), currency));
            lines.add(JournalLine.debit(positionEquivalent, equivalent, functional).withPosition(currency));
        }
    }

    /**
     * Balances the lines with an income or expense account. Income and expenses are
     * recorded in the functional currency at the spot rate (IAS 21 §21): in a foreign
     * currency the imbalance first goes through the FX position, so the bank's exposure
     * from it is revalued with the rest of the position, and the P&amp;L line is the
     * functional-currency equivalent.
     */
    private void balanceWith(List<JournalLine> lines, GlAccount incomeOrExpense, String currency) {
        String functional = functionalCurrency.get();
        if (!currency.equalsIgnoreCase(functional)) {
            addFxLeg(lines, currency, net(lines, currency).negate(), functional);
        }
        balanceResidual(lines, functional, incomeOrExpense);
    }

    /** Puts the functional-currency imbalance (rate and rounding differences) on {@code account}. */
    private static void balanceResidual(List<JournalLine> lines, String functional, GlAccount account) {
        BigDecimal net = net(lines, functional);
        if (net.signum() == 0) return;
        lines.add(net.signum() > 0
                ? JournalLine.credit(account, net, functional)
                : JournalLine.debit(account, net.negate(), functional));
    }

    /** Debits minus credits of the lines in {@code currency}. */
    private static BigDecimal net(List<JournalLine> lines, String currency) {
        return lines.stream()
                .filter(l -> l.currencyCode().equalsIgnoreCase(currency))
                .map(l -> l.side() == JournalEntry.EntryType.DEBIT ? l.amount() : l.amount().negate())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
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

    private static BigDecimal positivePart(BigDecimal v) {
        return v.signum() > 0 ? v : BigDecimal.ZERO;
    }
}
