package com.cba.product;

import com.cba.accounting.GlAccount;
import com.cba.charge.ChargeDefinition;
import com.cba.common.audit.AuditableEntity;
import com.cba.system.Fund;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "loan_products")
@Getter
@Setter
@NoArgsConstructor
public class LoanProduct extends AuditableEntity {

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

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode = "USD";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fund_id")
    private Fund fund;

    // ── Principal range ──────────────────────────────────────────────

    @Column(name = "min_principal", nullable = false, precision = 19, scale = 4)
    private BigDecimal minPrincipal;

    @Column(name = "max_principal", nullable = false, precision = 19, scale = 4)
    private BigDecimal maxPrincipal;

    @Column(name = "default_principal", precision = 19, scale = 4)
    private BigDecimal defaultPrincipal;

    @Column(name = "installment_amount_in_multiples_of")
    private Integer installmentAmountInMultiplesOf;

    // ── Interest rates ───────────────────────────────────────────────

    @Column(name = "min_interest_rate", nullable = false, precision = 8, scale = 4)
    private BigDecimal minInterestRate;

    @Column(name = "max_interest_rate", nullable = false, precision = 8, scale = 4)
    private BigDecimal maxInterestRate;

    @Column(name = "default_interest_rate", nullable = false, precision = 8, scale = 4)
    private BigDecimal defaultInterestRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "interest_rate_frequency_type", nullable = false, length = 20)
    private InterestRateFrequencyType interestRateFrequencyType = InterestRateFrequencyType.PER_YEAR;

    @Enumerated(EnumType.STRING)
    @Column(name = "interest_type", nullable = false, length = 20)
    private InterestType interestType = InterestType.DECLINING_BALANCE;

    @Enumerated(EnumType.STRING)
    @Column(name = "amortization_type", nullable = false, length = 30)
    private AmortizationType amortizationType = AmortizationType.EQUAL_INSTALLMENTS;

    @Enumerated(EnumType.STRING)
    @Column(name = "interest_calculation_period_type", nullable = false, length = 30)
    private InterestCalculationPeriodType interestCalculationPeriodType = InterestCalculationPeriodType.SAME_AS_REPAYMENT_PERIOD;

    @Enumerated(EnumType.STRING)
    @Column(name = "days_in_year_type", nullable = false, length = 20)
    private DaysInYearType daysInYearType = DaysInYearType.ACTUAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "days_in_month_type", nullable = false, length = 20)
    private DaysInMonthType daysInMonthType = DaysInMonthType.ACTUAL;

    // ── Repayment schedule ───────────────────────────────────────────

    @Column(name = "number_of_repayments", nullable = false)
    private int numberOfRepayments = 12;

    @Column(name = "repayment_every", nullable = false)
    private int repaymentEvery = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "repayment_frequency_type", nullable = false, length = 20)
    private RepaymentFrequencyType repaymentFrequencyType = RepaymentFrequencyType.MONTHS;

    @Enumerated(EnumType.STRING)
    @Column(name = "repayment_type", nullable = false, length = 30)
    private RepaymentType repaymentType = RepaymentType.ANNUITY;

    @Column(name = "min_term_months", nullable = false)
    private int minTermMonths;

    @Column(name = "max_term_months", nullable = false)
    private int maxTermMonths;

    // ── Grace periods ────────────────────────────────────────────────

    @Column(name = "grace_on_principal_payment")
    private Integer graceOnPrincipalPayment;

    @Column(name = "grace_on_interest_payment")
    private Integer graceOnInterestPayment;

    @Column(name = "grace_on_interest_charged")
    private Integer graceOnInterestCharged;

    @Column(name = "grace_on_arrears_ageing")
    private Integer graceOnArrearsAgeing;

    @Column(name = "in_arrears_tolerance", precision = 19, scale = 4)
    private BigDecimal inArrearsTolerance;

    // ── Fees ─────────────────────────────────────────────────────────

    @Column(name = "origination_fee", nullable = false, precision = 19, scale = 4)
    private BigDecimal originationFee = BigDecimal.ZERO;

    @Column(name = "late_payment_fee", nullable = false, precision = 19, scale = 4)
    private BigDecimal latePaymentFee = BigDecimal.ZERO;

    // ── Attribute overrides ──────────────────────────────────────────

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amortizationType",                    column = @Column(name = "allow_override_amortization_type")),
        @AttributeOverride(name = "interestType",                        column = @Column(name = "allow_override_interest_type")),
        @AttributeOverride(name = "repaymentEvery",                      column = @Column(name = "allow_override_repayment_every")),
        @AttributeOverride(name = "repaymentFrequency",                  column = @Column(name = "allow_override_repayment_frequency")),
        @AttributeOverride(name = "repaymentStrategy",                   column = @Column(name = "allow_override_repayment_strategy")),
        @AttributeOverride(name = "graceOnPrincipalAndInterestPayment",  column = @Column(name = "allow_override_grace_principal_interest")),
        @AttributeOverride(name = "graceOnInterestCharged",              column = @Column(name = "allow_override_grace_interest_charged")),
        @AttributeOverride(name = "interestRatePerPeriod",               column = @Column(name = "allow_override_interest_rate"))
    })
    private AllowAttributeOverrides allowAttributeOverrides = new AllowAttributeOverrides();

    // ── Accounting — GL account linkages (@ManyToOne, nullable) ─────

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fund_source_account_id")
    private GlAccount fundSourceAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_portfolio_account_id")
    private GlAccount loanPortfolioAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfers_in_suspense_account_id")
    private GlAccount transfersInSuspenseAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interest_on_loan_account_id")
    private GlAccount interestOnLoanAccount;

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
    @JoinColumn(name = "overpayment_liability_account_id")
    private GlAccount overpaymentLiabilityAccount;

    // ── Charges ──────────────────────────────────────────────────────

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "loan_product_charges",
        joinColumns = @JoinColumn(name = "loan_product_id"),
        inverseJoinColumns = @JoinColumn(name = "charge_definition_id")
    )
    private List<ChargeDefinition> charges = new ArrayList<>();

    // ── Status ───────────────────────────────────────────────────────

    @Column(nullable = false)
    private boolean active = true;
}
