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

    /** DR loan portfolio / CR the linked account's savings control (or overdraft). */
    public String postDisbursement(Loan loan, BigDecimal amount, LocalDate date) {
        String ccy = currency(loan);
        String no = loan.getLoanAccountNumber();
        String reference = "DISB-" + no;
        Account linked = loan.getLinkedAccount();
        if (linked == null) {
            throw CbaException.badRequest("NO_LINKED_ACCOUNT", "Loan " + no + " has no linked account to disburse into");
        }
        requireCurrency(accountRepository.findById(linked.getId())
                .orElseThrow(() -> CbaException.notFound("Account", linked.getId())), ccy);

        BalanceChange change = accountService.creditForSubledger(linked.getId(), amount,
                TransactionType.LOAN_DISBURSEMENT, "Loan disbursement: " + no, reference, "system");
        List<JournalLine> lines = new ArrayList<>();
        lines.add(JournalLine.debit(portfolio(loan.getProduct()), amount, ccy));
        lines.addAll(accountGl.balanceChangeLines(change.account(), change.before()));
        return gl.postJournal(lines, date, "Loan disbursement " + no, JournalEntry.EntityType.LOAN, loan.getId(), reference);
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

    private static void addCredit(List<JournalLine> lines, GlAccount account, BigDecimal amount, String ccy) {
        if (amount.signum() > 0) lines.add(JournalLine.credit(account, amount, ccy));
    }

    private static String reference(String kind, Loan loan) {
        return kind + "-" + loan.getLoanAccountNumber() + "-"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }
}
