package com.cba.loan;

import com.cba.account.Account;
import com.cba.account.AccountGlPosting;
import com.cba.account.AccountRepository;
import com.cba.account.AccountService;
import com.cba.account.AccountService.BalanceChange;
import com.cba.account.TransactionType;
import com.cba.accounting.FinancialActivityAccount.FinancialActivity;
import com.cba.accounting.GlAccount;
import com.cba.accounting.GlAccountRepository;
import com.cba.accounting.GlAccountingService;
import com.cba.accounting.GlAccountingService.JournalLine;
import com.cba.accounting.JournalEntry;
import com.cba.charge.LoanCharge;
import com.cba.common.exception.CbaException;
import com.cba.product.LoanProduct;
import com.cba.teller.CashTransactionType;
import com.cba.teller.TellerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Posts loan money movements to the general ledger, in the same transaction as the
 * movement. Loans are accounted for on an accrual basis (IFRS 9 §5.4.1): income is
 * recognised against a receivable when earned or charged, and cash received clears the
 * receivable, so a repayment never touches profit or loss. The portfolio plus the loan's
 * receivables is its gross carrying amount.
 *
 * <p>GL accounts come from the loan product's links first, then the financial-activity
 * mapping; with neither the movement is rejected ({@code ACTIVITY_NOT_MAPPED}).
 */
@Component
@RequiredArgsConstructor
public class LoanGlPosting {

    private final GlAccountingService gl;
    private final AccountGlPosting accountGl;
    private final AccountService accountService;
    private final AccountRepository accountRepository;
    private final TellerService tellerService;
    private final GlAccountRepository glAccountRepository;

    /** How a loan payment is split; each part clears its own asset account. */
    public record Allocation(BigDecimal principal, BigDecimal interest, BigDecimal fees) {
        public BigDecimal total() {
            return principal.add(interest).add(fees);
        }
    }

    /**
     * DR loan portfolio (principal) / CR the linked account (principal − fee) / CR deferred
     * loan fees (fee). The fee is deducted from the proceeds and deferred: it is part of
     * the effective interest rate (IFRS 9 B5.4.2), released to interest income by accrual.
     */
    public String postDisbursement(Loan loan, BigDecimal principal, BigDecimal fee, LocalDate date) {
        String ccy = currency(loan);
        String no = loan.getLoanAccountNumber();
        String reference = "DISB-" + no;
        Account linked = loan.getLinkedAccount();
        if (linked == null) {
            throw CbaException.badRequest("NO_LINKED_ACCOUNT", "Loan " + no + " has no linked account to disburse into");
        }
        requireCurrency(accountRepository.findById(linked.getId())
                .orElseThrow(() -> CbaException.notFound("Account", linked.getId())), ccy);

        BalanceChange change = accountService.creditForSubledger(linked.getId(), principal.subtract(fee),
                TransactionType.LOAN_DISBURSEMENT, "Loan disbursement: " + no, reference, "system");
        List<JournalLine> lines = new ArrayList<>();
        lines.add(JournalLine.debit(portfolio(loan.getProduct()), principal, ccy));
        lines.addAll(accountGl.balanceChangeLines(change.account(), change.before()));
        addCredit(lines, gl.activityAccount(FinancialActivity.ASSET_DEFERRED_LOAN_FEES), fee, ccy);
        return gl.postJournal(lines, date, "Loan disbursement " + no, JournalEntry.EntityType.LOAN, loan.getId(), reference);
    }

    /**
     * One accrual run for a loan. Interest: DR interest receivable / CR interest income, or
     * CR income in suspense when the loan is non-performing (CBN). Fee: DR deferred loan
     * fees / CR interest income — the fee was received in cash, so it is released even on
     * a non-performing loan. Returns null when there is nothing to post.
     */
    public String postAccrual(Loan loan, BigDecimal interest, BigDecimal fee, boolean toSuspense, LocalDate date) {
        if (interest.signum() <= 0 && fee.signum() <= 0) return null;
        String ccy = currency(loan);
        List<JournalLine> lines = new ArrayList<>();
        addDebit(lines, gl.activityAccount(FinancialActivity.ASSET_INTEREST_RECEIVABLE), interest, ccy);
        addDebit(lines, gl.activityAccount(FinancialActivity.ASSET_DEFERRED_LOAN_FEES), fee, ccy);
        if (toSuspense) {
            addCredit(lines, suspense(), interest, ccy);
            accountGl.addProfitOrLoss(lines, interestIncome(loan.getProduct()), fee, JournalEntry.EntryType.CREDIT, ccy);
        } else {
            accountGl.addProfitOrLoss(lines, interestIncome(loan.getProduct()), interest.add(fee),
                    JournalEntry.EntryType.CREDIT, ccy);
        }
        return gl.postJournal(lines, date, "Interest accrual " + loan.getLoanAccountNumber() + " to " + date,
                JournalEntry.EntityType.LOAN, loan.getId(), reference("ACR", loan));
    }

    /**
     * The loan has become non-performing: income already recognised but not received
     * leaves profit or loss for suspense (CBN). DR interest income / CR suspense for the
     * interest; DR each penalty's income account / CR suspense for unpaid penalties.
     */
    public String postSuspension(Loan loan, BigDecimal interest, List<LoanCharge> penalties, LocalDate date) {
        BigDecimal penaltyTotal = penalties.stream().map(LoanCharge::getAmountOutstanding)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (interest.signum() <= 0 && penaltyTotal.signum() <= 0) return null;
        String ccy = currency(loan);
        List<JournalLine> lines = new ArrayList<>();
        addCredit(lines, suspense(), interest.add(penaltyTotal), ccy);
        accountGl.addProfitOrLoss(lines, interestIncome(loan.getProduct()), interest, JournalEntry.EntryType.DEBIT, ccy);
        for (LoanCharge c : penalties) {
            accountGl.addProfitOrLoss(lines, chargeIncome(loan.getProduct(), c), c.getAmountOutstanding(),
                    JournalEntry.EntryType.DEBIT, ccy);
        }
        return gl.postJournal(lines, date, "Loan " + loan.getLoanAccountNumber() + " non-performing: income to suspense",
                JournalEntry.EntityType.LOAN, loan.getId(), reference("SUSP", loan));
    }

    /** Interest held in suspense has been received in cash: DR suspense / CR interest income. */
    public String postInterestSuspenseRelease(Loan loan, BigDecimal amount, LocalDate date) {
        return postSuspenseRelease(loan, interestIncome(loan.getProduct()), amount, date, "Interest received");
    }

    /** A suspended penalty has been paid: DR suspense / CR its income account. */
    public String postChargeSuspenseRelease(Loan loan, LoanCharge charge, BigDecimal amount, LocalDate date) {
        return postSuspenseRelease(loan, chargeIncome(loan.getProduct(), charge), amount, date,
                "Charge " + charge.getName() + " received");
    }

    /** A penalty charged on a non-performing loan: DR fees receivable / CR suspense (not income). */
    public String postChargeToSuspense(Loan loan, LoanCharge charge, BigDecimal amount, LocalDate date) {
        String ccy = currency(loan);
        List<JournalLine> lines = new ArrayList<>();
        lines.add(JournalLine.debit(gl.activityAccount(FinancialActivity.ASSET_FEES_RECEIVABLE), amount, ccy));
        lines.add(JournalLine.credit(suspense(), amount, ccy));
        return gl.postJournal(lines, date, "Penalty " + charge.getName() + " on non-performing loan "
                + loan.getLoanAccountNumber() + " (suspense)", JournalEntry.EntityType.LOAN, loan.getId(), reference("CHGS", loan));
    }

    /** A suspended charge is waived: it was never income, so DR suspense / CR fees receivable. */
    public String postSuspendedChargeWaiver(Loan loan, LoanCharge charge, BigDecimal amount, LocalDate date) {
        String ccy = currency(loan);
        List<JournalLine> lines = new ArrayList<>();
        lines.add(JournalLine.debit(suspense(), amount, ccy));
        lines.add(JournalLine.credit(gl.activityAccount(FinancialActivity.ASSET_FEES_RECEIVABLE), amount, ccy));
        return gl.postJournal(lines, date, "Suspended charge " + charge.getName() + " waived on loan "
                + loan.getLoanAccountNumber(), JournalEntry.EntityType.LOAN, loan.getId(), reference("CHGW", loan));
    }

    /**
     * The loan is settled. Interest received ahead of accrual and the origination fee not
     * yet released are income now: the loan is derecognised (IFRS 9 §3.2.3, B5.4.6).
     * DR interest receivable / DR deferred loan fees / CR interest income.
     */
    public String postCloseOut(Loan loan, BigDecimal prepaidInterest, BigDecimal deferredFee, LocalDate date) {
        if (prepaidInterest.signum() <= 0 && deferredFee.signum() <= 0) return null;
        String ccy = currency(loan);
        List<JournalLine> lines = new ArrayList<>();
        addDebit(lines, gl.activityAccount(FinancialActivity.ASSET_INTEREST_RECEIVABLE), prepaidInterest, ccy);
        addDebit(lines, gl.activityAccount(FinancialActivity.ASSET_DEFERRED_LOAN_FEES), deferredFee, ccy);
        accountGl.addProfitOrLoss(lines, interestIncome(loan.getProduct()),
                prepaidInterest.max(BigDecimal.ZERO).add(deferredFee.max(BigDecimal.ZERO)), JournalEntry.EntryType.CREDIT, ccy);
        return gl.postJournal(lines, date, "Loan " + loan.getLoanAccountNumber() + " settled: remaining income recognised",
                JournalEntry.EntityType.LOAN, loan.getId(), reference("CLOSE", loan));
    }

    private String postSuspenseRelease(Loan loan, GlAccount income, BigDecimal amount, LocalDate date, String what) {
        if (amount.signum() <= 0) return null;
        String ccy = currency(loan);
        List<JournalLine> lines = new ArrayList<>();
        lines.add(JournalLine.debit(suspense(), amount, ccy));
        accountGl.addProfitOrLoss(lines, income, amount, JournalEntry.EntryType.CREDIT, ccy);
        return gl.postJournal(lines, date, what + " on non-performing loan " + loan.getLoanAccountNumber()
                + ": released from suspense", JournalEntry.EntityType.LOAN, loan.getId(), reference("SREL", loan));
    }

    /**
     * Collects {@code allocation.total()} from the source and clears the loan's assets:
     * CR portfolio (principal), interest receivable (interest), fees receivable (fees).
     * {@code kind} prefixes the reference, e.g. RPMT or FCLS.
     */
    public String postRepayment(Loan loan, LoanPaymentSource source, Allocation allocation,
                                LocalDate date, String kind, String description) {
        String ccy = currency(loan);
        String reference = reference(kind, loan);
        List<JournalLine> lines = collect(loan, source, allocation.total(), TransactionType.LOAN_REPAYMENT,
                description, reference);
        addCredit(lines, portfolio(loan.getProduct()), allocation.principal(), ccy);
        addCredit(lines, gl.activityAccount(FinancialActivity.ASSET_INTEREST_RECEIVABLE), allocation.interest(), ccy);
        addCredit(lines, gl.activityAccount(FinancialActivity.ASSET_FEES_RECEIVABLE), allocation.fees(), ccy);
        return gl.postJournal(lines, date, description, JournalEntry.EntityType.LOAN, loan.getId(), reference);
    }

    /** A charge becomes income: DR fees receivable / CR fee or penalty income. */
    public String postChargeRecognition(Loan loan, LoanCharge charge, BigDecimal amount, LocalDate date) {
        String ccy = currency(loan);
        List<JournalLine> lines = new ArrayList<>();
        lines.add(JournalLine.debit(gl.activityAccount(FinancialActivity.ASSET_FEES_RECEIVABLE), amount, ccy));
        accountGl.addProfitOrLoss(lines, chargeIncome(loan.getProduct(), charge), amount, JournalEntry.EntryType.CREDIT, ccy);
        return gl.postJournal(lines, date, (charge.isPenalty() ? "Penalty " : "Fee ") + charge.getName()
                + " on loan " + loan.getLoanAccountNumber(), JournalEntry.EntityType.LOAN, loan.getId(), reference("CHG", loan));
    }

    /** A charge is paid: DR the money source / CR fees receivable. */
    public String postChargePayment(Loan loan, LoanCharge charge, LoanPaymentSource source,
                                    BigDecimal amount, LocalDate date) {
        String ccy = currency(loan);
        String reference = reference("CHGP", loan);
        String description = "Charge " + charge.getName() + " paid on loan " + loan.getLoanAccountNumber();
        List<JournalLine> lines = collect(loan, source, amount, TransactionType.FEE_CHARGE, description, reference);
        addCredit(lines, gl.activityAccount(FinancialActivity.ASSET_FEES_RECEIVABLE), amount, ccy);
        return gl.postJournal(lines, date, description, JournalEntry.EntityType.LOAN, loan.getId(), reference);
    }

    /**
     * A recognised charge is waived. The bank gives up revenue it had recognised, a
     * change in the transaction price (IFRS 15 §87-88): DR the income account / CR fees
     * receivable.
     */
    public String postChargeWaiver(Loan loan, LoanCharge charge, BigDecimal amount, LocalDate date) {
        String ccy = currency(loan);
        List<JournalLine> lines = new ArrayList<>();
        lines.add(JournalLine.credit(gl.activityAccount(FinancialActivity.ASSET_FEES_RECEIVABLE), amount, ccy));
        accountGl.addProfitOrLoss(lines, chargeIncome(loan.getProduct(), charge), amount, JournalEntry.EntryType.DEBIT, ccy);
        return gl.postJournal(lines, date, "Charge " + charge.getName() + " waived on loan "
                + loan.getLoanAccountNumber(), JournalEntry.EntityType.LOAN, loan.getId(), reference("CHGW", loan));
    }

    /** The loan's currency: its product's. */
    public static String currency(Loan loan) {
        return loan.getProduct().getCurrencyCode();
    }

    // ── Money source ─────────────────────────────────────────────────────────

    /**
     * Takes {@code amount} from the source and returns the journal lines for that side:
     * the account's balance change, or DR Cash at Teller with the cash recorded in the till.
     */
    private List<JournalLine> collect(Loan loan, LoanPaymentSource source, BigDecimal amount,
                                      TransactionType type, String description, String reference) {
        String ccy = currency(loan);
        if (source.method() == LoanPaymentSource.Method.CASH) {
            tellerService.recordLoanCash(source.tellerSessionId(), CashTransactionType.CASH_IN, amount, ccy,
                    loan.getId(), description);
            List<JournalLine> lines = new ArrayList<>();
            lines.add(JournalLine.debit(gl.activityAccount(FinancialActivity.ASSET_CASH_AT_TELLER), amount, ccy));
            return lines;
        }
        UUID accountId = source.accountId() != null ? source.accountId()
                : loan.getLinkedAccount() != null ? loan.getLinkedAccount().getId() : null;
        if (accountId == null) {
            throw CbaException.badRequest("NO_LINKED_ACCOUNT",
                    "Loan " + loan.getLoanAccountNumber() + " has no linked account; name sourceAccountId or pay in cash");
        }
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> CbaException.notFound("Account", accountId));
        if (!account.getCustomer().getId().equals(loan.getCustomer().getId())) {
            throw CbaException.badRequest("SOURCE_ACCOUNT_NOT_BORROWERS",
                    "Account " + account.getAccountNumber() + " does not belong to the borrower");
        }
        requireCurrency(account, ccy);
        BalanceChange change = accountService.debitForSubledger(accountId, amount, type, description, reference, "system");
        return new ArrayList<>(accountGl.balanceChangeLines(change.account(), change.before()));
    }

    private static void requireCurrency(Account account, String loanCurrency) {
        if (!account.getCurrencyCode().equalsIgnoreCase(loanCurrency)) {
            throw CbaException.badRequest("CURRENCY_MISMATCH", "Account " + account.getAccountNumber()
                    + " is in " + account.getCurrencyCode() + "; the loan is in " + loanCurrency);
        }
    }

    // ── Account resolution: charge, then product link, then activity mapping ─

    private GlAccount portfolio(LoanProduct p) {
        return p.getLoanPortfolioAccount() != null
                ? p.getLoanPortfolioAccount() : gl.activityAccount(FinancialActivity.ASSET_LOAN_PORTFOLIO);
    }

    private GlAccount chargeIncome(LoanProduct p, LoanCharge charge) {
        UUID own = charge.getChargeDefinition() != null ? charge.getChargeDefinition().getIncomeAccountId() : null;
        if (own != null) {
            return glAccountRepository.findById(own).orElseThrow(() -> CbaException.notFound("GlAccount", own));
        }
        if (charge.isPenalty()) {
            return p.getIncomeFromPenaltiesAccount() != null
                    ? p.getIncomeFromPenaltiesAccount() : gl.activityAccount(FinancialActivity.INCOME_PENALTIES);
        }
        return p.getIncomeFromFeesAccount() != null
                ? p.getIncomeFromFeesAccount() : gl.activityAccount(FinancialActivity.INCOME_FEES);
    }

    private GlAccount interestIncome(LoanProduct p) {
        return p.getInterestOnLoanAccount() != null
                ? p.getInterestOnLoanAccount() : gl.activityAccount(FinancialActivity.INCOME_INTEREST);
    }

    private GlAccount suspense() {
        return gl.activityAccount(FinancialActivity.LIABILITY_INCOME_IN_SUSPENSE);
    }

    private static void addCredit(List<JournalLine> lines, GlAccount account, BigDecimal amount, String ccy) {
        if (amount.signum() > 0) lines.add(JournalLine.credit(account, amount, ccy));
    }

    private static void addDebit(List<JournalLine> lines, GlAccount account, BigDecimal amount, String ccy) {
        if (amount.signum() > 0) lines.add(JournalLine.debit(account, amount, ccy));
    }

    private static String reference(String kind, Loan loan) {
        return kind + "-" + loan.getLoanAccountNumber() + "-"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }
}
