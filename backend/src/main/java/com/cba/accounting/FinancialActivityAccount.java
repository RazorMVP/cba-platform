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
        /** Loan fees and penalties charged and not yet paid. */
        ASSET_FEES_RECEIVABLE,
        /** Contra-asset: origination fees not yet released to interest income (IFRS 9 B5.4.2). */
        ASSET_DEFERRED_LOAN_FEES,
        /** Interest and penalties on non-performing loans, not income until received (CBN). */
        LIABILITY_INCOME_IN_SUSPENSE,
        LIABILITY_SAVINGS_CONTROL,
        LIABILITY_TRANSFER_IN_SUSPENSE,
        /** Cleared card transactions owed to the card schemes until they settle (GL 2300, V63). */
        LIABILITY_CARD_SETTLEMENT,
        /** The bank's account at its card settlement bank: scheme net settlements go through it (GL 1003, V64). */
        ASSET_CARD_SCHEME_SETTLEMENT,
        /** Interchange earned as card issuer on cleared purchases (GL 4005, V64; IFRS 15 B35, B37, §38). */
        INCOME_CARD_INTERCHANGE,
        /** Card scheme assessment and network fees (GL 5006, V64; shown gross, IAS 1 §32). */
        EXPENSE_CARD_SCHEME_FEES,
        /** Interchange paid to acquirers on cardholders' cash at other banks' ATMs (GL 5007, V64). */
        EXPENSE_CARD_INTERCHANGE,
        INCOME_INTEREST,
        INCOME_FEES,
        /** Penalties charged on loans: income when charged. */
        INCOME_PENALTIES,
        /** Contra-asset: IFRS 9 expected credit losses on loans (credit balance). */
        ASSET_LOAN_LOSS_ALLOWANCE,
        /** Impairment gains and losses on loans, incl. recoveries of amounts written off (IFRS 9 §5.5.8). */
        EXPENSE_LOAN_LOSS_PROVISION,
        /** Contra-asset: IFRS 9 §5.4.3 modification losses on loans not yet unwound into income. */
        ASSET_LOAN_MODIFICATION_DISCOUNT,
        /** Interest waived on loans: accrued interest derecognised and future interest given up. */
        EXPENSE_LOAN_MODIFICATION_LOSS,
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
        EXPENSE_CASH_OVER_SHORT,
        /**
         * Offset of the opening-balance journal and of the legacy trial balance loaded
         * against it. Must be zero in every currency once migration is complete.
         */
        EQUITY_MIGRATION_CLEARING,
        /** Retained earnings (general reserve): the source of regulatory risk reserve transfers. */
        EQUITY_RETAINED_EARNINGS,
        /** Non-distributable: CBN prudential provisions in excess of the IFRS 9 allowance. */
        EQUITY_REGULATORY_RISK_RESERVE
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
