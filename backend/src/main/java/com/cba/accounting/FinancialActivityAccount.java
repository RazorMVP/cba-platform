package com.cba.accounting;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Maps abstract financial activities (e.g. ASSET_FUND_SOURCE) to concrete GL account codes.
 * Populated at setup; used by auto-posting logic to resolve debit/credit GL accounts
 * without hard-coding account numbers in business services.
 */
@Entity
@Table(name = "financial_activity_accounts")
@Getter @Setter @NoArgsConstructor
public class FinancialActivityAccount {

    public enum FinancialActivity {
        ASSET_FUND_SOURCE,
        ASSET_CASH_AT_TELLER,
        ASSET_INTEREST_RECEIVABLE,
        ASSET_LOAN_PORTFOLIO,
        LIABILITY_SAVINGS_CONTROL,
        LIABILITY_TRANSFER_IN_SUSPENSE,
        INCOME_INTEREST,
        INCOME_FEES,
        EXPENSE_LOAN_LOSS_PROVISION,
        EXPENSE_WRITE_OFF,
        /** Debit side of interest credited to savings; credit side is LIABILITY_SAVINGS_CONTROL. */
        EXPENSE_INTEREST_ON_SAVINGS,
        /** Overdrawn deposit balances: a loan to the customer, not a negative deposit. */
        ASSET_OVERDRAFT_PORTFOLIO,
        /** Cross-currency position, posted in each foreign currency. */
        ASSET_FX_POSITION,
        /** Functional-currency equivalent of ASSET_FX_POSITION. */
        ASSET_FX_POSITION_EQUIVALENT,
        /** Realised and revaluation exchange differences (IAS 21 §28). */
        INCOME_FX_GAIN_LOSS,
        /** Teller cash count differences at session close. */
        EXPENSE_CASH_OVER_SHORT
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 60)
    private FinancialActivity financialActivity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gl_account_id", nullable = false)
    private GlAccount glAccount;
}
