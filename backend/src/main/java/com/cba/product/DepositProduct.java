package com.cba.product;

import com.cba.accounting.GlAccount;
import com.cba.charge.ChargeDefinition;
import com.cba.common.audit.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "deposit_products")
@Getter
@Setter
@NoArgsConstructor
public class DepositProduct extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    // ── Core identifiers ─────────────────────────────────────────────

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "short_name", nullable = false, unique = true, length = 4)
    private String shortName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 30)
    private DepositAccountType accountType;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode = "USD";

    // ── Balance constraints ──────────────────────────────────────────

    @Column(name = "minimum_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal minimumBalance = BigDecimal.ZERO;

    @Column(name = "min_required_opening_balance", precision = 19, scale = 4)
    private BigDecimal minRequiredOpeningBalance;

    // ── Interest configuration ───────────────────────────────────────

    @Column(name = "interest_rate", nullable = false, precision = 8, scale = 4)
    private BigDecimal interestRate = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "interest_compounding", nullable = false, length = 20)
    private InterestCompounding interestCompounding = InterestCompounding.MONTHLY;

    @Enumerated(EnumType.STRING)
    @Column(name = "interest_posting_period_type", nullable = false, length = 20)
    private InterestPostingPeriodType interestPostingPeriodType = InterestPostingPeriodType.MONTHLY;

    @Enumerated(EnumType.STRING)
    @Column(name = "days_in_year_type", nullable = false, length = 20)
    private DaysInYearType daysInYearType = DaysInYearType.ACTUAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "days_in_month_type", nullable = false, length = 20)
    private DaysInMonthType daysInMonthType = DaysInMonthType.ACTUAL;

    // ── Lock-in period ───────────────────────────────────────────────

    @Column(name = "lockin_period_frequency")
    private Integer lockinPeriodFrequency;

    @Enumerated(EnumType.STRING)
    @Column(name = "lockin_period_frequency_type", length = 20)
    private LockInFrequencyType lockinPeriodFrequencyType;

    // ── Withdrawal settings ──────────────────────────────────────────

    @Column(name = "withdrawal_fee_for_transfers", nullable = false)
    private boolean withdrawalFeeForTransfers = false;

    // ── Overdraft ────────────────────────────────────────────────────

    @Column(name = "allow_overdraft", nullable = false)
    private boolean allowOverdraft = false;

    @Column(name = "overdraft_limit", precision = 19, scale = 4)
    private BigDecimal overdraftLimit;

    @Column(name = "nominal_annual_interest_rate_overdraft", precision = 8, scale = 4)
    private BigDecimal nominalAnnualInterestRateOverdraft;

    @Column(name = "min_overdraft_for_interest_calculation", precision = 19, scale = 4)
    private BigDecimal minOverdraftForInterestCalculation;

    // ── Accounting type ──────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "accounting_type", nullable = false, length = 20)
    private AccountingType accountingType = AccountingType.NONE;

    // ── Accounting — GL account linkages (@ManyToOne, nullable) ─────

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "savings_reference_account_id")
    private GlAccount savingsReferenceAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "savings_control_account_id")
    private GlAccount savingsControlAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfers_in_suspense_account_id")
    private GlAccount transfersInSuspenseAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interest_on_savings_account_id")
    private GlAccount interestOnSavingsAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "income_from_fees_account_id")
    private GlAccount incomeFromFeesAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "income_from_penalties_account_id")
    private GlAccount incomeFromPenaltiesAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "write_off_account_id")
    private GlAccount writeOffAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "overdraft_portfolio_control_account_id")
    private GlAccount overdraftPortfolioControlAccount;

    // ── Charges ──────────────────────────────────────────────────────

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "deposit_product_charges",
        joinColumns = @JoinColumn(name = "deposit_product_id"),
        inverseJoinColumns = @JoinColumn(name = "charge_definition_id")
    )
    private List<ChargeDefinition> charges = new ArrayList<>();

    // ── Status ───────────────────────────────────────────────────────

    @Column(nullable = false)
    private boolean active = true;
}
