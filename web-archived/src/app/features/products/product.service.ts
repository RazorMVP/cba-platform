import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from '../../core/api/api.service';

// ── Shared refs ──────────────────────────────────────────────────────────────

export interface GlAccountRef { id: string; glCode: string; name: string; }
export interface FundRef       { id: string; name: string; }
export interface ChargeRef     { id: string; name: string; chargeTimeType: string; }

export interface AllowAttributeOverrides {
  amortizationType:                   boolean;
  interestType:                       boolean;
  repaymentEvery:                     boolean;
  repaymentFrequency:                 boolean;
  repaymentStrategy:                  boolean;
  graceOnPrincipalAndInterestPayment: boolean;
  graceOnInterestCharged:             boolean;
  interestRatePerPeriod:              boolean;
}

// ── Loan Product ─────────────────────────────────────────────────────────────

export interface LoanProduct {
  id: string;
  name: string;
  shortName: string;
  description?: string;
  currencyCode: string;
  fund?: FundRef;

  // Principal
  minPrincipal: number;
  maxPrincipal: number;
  defaultPrincipal?: number;
  installmentAmountInMultiplesOf?: number;

  // Interest
  minInterestRate: number;
  maxInterestRate: number;
  defaultInterestRate: number;
  interestRateFrequencyType: string;
  interestType: string;
  amortizationType: string;
  interestCalculationPeriodType: string;
  daysInYearType: string;
  daysInMonthType: string;

  // Repayment schedule
  minTermMonths: number;
  maxTermMonths: number;
  numberOfRepayments: number;
  repaymentEvery: number;
  repaymentFrequencyType: string;
  repaymentType: string;

  // Grace periods
  graceOnPrincipalPayment?: number;
  graceOnInterestPayment?: number;
  graceOnInterestCharged?: number;
  graceOnArrearsAgeing?: number;
  inArrearsTolerance?: number;

  // Fees
  originationFee: number;
  latePaymentFee: number;

  // Overrides & accounting
  allowAttributeOverrides: AllowAttributeOverrides;
  fundSourceAccount?: GlAccountRef;
  loanPortfolioAccount?: GlAccountRef;
  transfersInSuspenseAccount?: GlAccountRef;
  interestOnLoanAccount?: GlAccountRef;
  incomeFromFeesAccount?: GlAccountRef;
  incomeFromPenaltiesAccount?: GlAccountRef;
  writeOffAccount?: GlAccountRef;
  overpaymentLiabilityAccount?: GlAccountRef;

  charges: ChargeRef[];
  active: boolean;
}

export interface LoanProductCreateRequest {
  name: string;
  shortName: string;
  description?: string;
  currencyCode?: string;
  fundId?: string;
  minPrincipal: number;
  maxPrincipal: number;
  defaultPrincipal?: number;
  installmentAmountInMultiplesOf?: number;
  minInterestRate: number;
  maxInterestRate: number;
  defaultInterestRate: number;
  interestRateFrequencyType?: string;
  interestType?: string;
  amortizationType?: string;
  interestCalculationPeriodType?: string;
  daysInYearType?: string;
  daysInMonthType?: string;
  minTermMonths: number;
  maxTermMonths: number;
  numberOfRepayments?: number;
  repaymentEvery?: number;
  repaymentFrequencyType?: string;
  repaymentType?: string;
  graceOnPrincipalPayment?: number;
  graceOnInterestPayment?: number;
  graceOnInterestCharged?: number;
  graceOnArrearsAgeing?: number;
  inArrearsTolerance?: number;
  originationFee?: number;
  latePaymentFee?: number;
  allowAttributeOverrides?: Partial<AllowAttributeOverrides>;
  fundSourceAccountId?: string;
  loanPortfolioAccountId?: string;
  transfersInSuspenseAccountId?: string;
  interestOnLoanAccountId?: string;
  incomeFromFeesAccountId?: string;
  incomeFromPenaltiesAccountId?: string;
  writeOffAccountId?: string;
  overpaymentLiabilityAccountId?: string;
  chargeIds?: string[];
}

// ── Deposit Product ───────────────────────────────────────────────────────────

export interface DepositProduct {
  id: string;
  name: string;
  shortName: string;
  description?: string;
  accountType: string;
  currencyCode: string;

  // Balance
  minimumBalance: number;
  minRequiredOpeningBalance?: number;

  // Interest
  interestRate: number;
  interestCompounding: string;
  interestPostingPeriodType: string;
  daysInYearType: string;
  daysInMonthType: string;

  // Lock-in
  lockinPeriodFrequency?: number;
  lockinPeriodFrequencyType?: string;

  // Withdrawal & overdraft
  withdrawalFeeForTransfers: boolean;
  allowOverdraft: boolean;
  overdraftLimit?: number;
  nominalAnnualInterestRateOverdraft?: number;
  minOverdraftForInterestCalculation?: number;

  // Accounting & GL
  accountingType: string;
  savingsReferenceAccount?: GlAccountRef;
  savingsControlAccount?: GlAccountRef;
  transfersInSuspenseAccount?: GlAccountRef;
  interestOnSavingsAccount?: GlAccountRef;
  incomeFromFeesAccount?: GlAccountRef;
  incomeFromPenaltiesAccount?: GlAccountRef;
  writeOffAccount?: GlAccountRef;
  overdraftPortfolioControlAccount?: GlAccountRef;

  charges: ChargeRef[];
  active: boolean;
}

export interface DepositProductCreateRequest {
  name: string;
  shortName: string;
  description?: string;
  accountType: string;
  currencyCode?: string;
  minimumBalance?: number;
  minRequiredOpeningBalance?: number;
  interestRate?: number;
  interestCompounding?: string;
  interestPostingPeriodType?: string;
  daysInYearType?: string;
  daysInMonthType?: string;
  lockinPeriodFrequency?: number;
  lockinPeriodFrequencyType?: string;
  withdrawalFeeForTransfers?: boolean;
  allowOverdraft?: boolean;
  overdraftLimit?: number;
  nominalAnnualInterestRateOverdraft?: number;
  minOverdraftForInterestCalculation?: number;
  accountingType?: string;
  savingsReferenceAccountId?: string;
  savingsControlAccountId?: string;
  transfersInSuspenseAccountId?: string;
  interestOnSavingsAccountId?: string;
  incomeFromFeesAccountId?: string;
  incomeFromPenaltiesAccountId?: string;
  writeOffAccountId?: string;
  overdraftPortfolioControlAccountId?: string;
  chargeIds?: string[];
}

// ── Fixed Deposit Product ─────────────────────────────────────────────────────

export interface FixedDepositProduct {
  id: string;
  name: string;
  shortName: string;
  description?: string;
  currencyCode: string;
  nominalAnnualInterestRate: number;
  compoundingPeriod: string;
  postingPeriod: string;
  calculationType: string;
  minDepositTerm: number;
  minDepositTermType: string;
  maxDepositTerm?: number;
  maxDepositTermType?: string;
  minDepositAmount?: number;
  maxDepositAmount?: number;
  prePenaltyApplicable: boolean;
  prePenaltyInterest?: number;
  active: boolean;
}

export interface FixedDepositProductRequest {
  name: string;
  shortName?: string;
  description?: string;
  currencyCode?: string;
  nominalAnnualInterestRate: number;
  compoundingPeriod?: string;
  postingPeriod?: string;
  calculationType?: string;
  minDepositTerm: number;
  minDepositTermType?: string;
  maxDepositTerm?: number;
  maxDepositTermType?: string;
  minDepositAmount?: number;
  maxDepositAmount?: number;
  prePenaltyApplicable?: boolean;
  prePenaltyInterest?: number;
}

// ── Recurring Deposit Product ─────────────────────────────────────────────────

export interface RecurringDepositProduct {
  id: string;
  name: string;
  shortName: string;
  description?: string;
  currencyCode: string;
  nominalAnnualInterestRate: number;
  compoundingPeriod: string;
  postingPeriod: string;
  calculationType: string;
  depositFrequency: string;
  mandatoryRecommendedDepositAmount?: number;
  minDepositAmount?: number;
  maxDepositAmount?: number;
  minDepositTerm: number;
  minDepositTermType: string;
  maxDepositTerm?: number;
  maxDepositTermType?: string;
  prePenaltyApplicable: boolean;
  prePenaltyInterest?: number;
  active: boolean;
}

export interface RecurringDepositProductRequest {
  name: string;
  shortName?: string;
  description?: string;
  currencyCode?: string;
  nominalAnnualInterestRate: number;
  compoundingPeriod?: string;
  postingPeriod?: string;
  calculationType?: string;
  depositFrequency?: string;
  mandatoryRecommendedDepositAmount?: number;
  minDepositAmount?: number;
  maxDepositAmount?: number;
  minDepositTerm: number;
  minDepositTermType?: string;
  maxDepositTerm?: number;
  maxDepositTermType?: string;
  prePenaltyApplicable?: boolean;
  prePenaltyInterest?: number;
}

// ── Share Product ─────────────────────────────────────────────────────────────

export interface ShareProduct {
  id: string;
  name: string;
  shortName: string;
  description?: string;
  currencyCode: string;
  totalShares?: number;
  sharesIssued: number;
  unitPrice?: number;
  nominalShares?: number;
  minimumShares?: number;
  maximumShares?: number;
  minimumActivePeriodFrequency?: number;
  minimumActivePeriodFrequencyType?: string;
  lockInPeriodFrequency?: number;
  lockInPeriodFrequencyType?: string;
  allowDividendsForInactive: boolean;
  active: boolean;
}

export interface ShareProductRequest {
  name: string;
  shortName: string;
  description?: string;
  currencyCode?: string;
  totalShares?: number;
  unitPrice?: number;
  nominalShares?: number;
  minimumShares?: number;
  maximumShares?: number;
  minimumActivePeriodFrequency?: number;
  minimumActivePeriodFrequencyType?: string;
  lockInPeriodFrequency?: number;
  lockInPeriodFrequencyType?: string;
  allowDividendsForInactive?: boolean;
}

// ── Service ───────────────────────────────────────────────────────────────────

@Injectable({ providedIn: 'root' })
export class ProductService {
  private readonly api = inject(ApiService);

  // Loan Products
  listLoanProducts(activeOnly = false): Observable<LoanProduct[]> {
    return this.api.get<LoanProduct[]>('/loan-products', { activeOnly: String(activeOnly) });
  }

  getLoanProduct(id: string): Observable<LoanProduct> {
    return this.api.get<LoanProduct>(`/loan-products/${id}`);
  }

  createLoanProduct(body: LoanProductCreateRequest): Observable<LoanProduct> {
    return this.api.post<LoanProduct>('/loan-products', body);
  }

  updateLoanProduct(id: string, body: LoanProductCreateRequest): Observable<LoanProduct> {
    return this.api.put<LoanProduct>(`/loan-products/${id}`, body);
  }

  deactivateLoanProduct(id: string): Observable<void> {
    return this.api.delete<void>(`/loan-products/${id}`);
  }

  // Deposit Products
  listDepositProducts(activeOnly = false): Observable<DepositProduct[]> {
    return this.api.get<DepositProduct[]>('/deposit-products', { activeOnly: String(activeOnly) });
  }

  getDepositProduct(id: string): Observable<DepositProduct> {
    return this.api.get<DepositProduct>(`/deposit-products/${id}`);
  }

  createDepositProduct(body: DepositProductCreateRequest): Observable<DepositProduct> {
    return this.api.post<DepositProduct>('/deposit-products', body);
  }

  updateDepositProduct(id: string, body: DepositProductCreateRequest): Observable<DepositProduct> {
    return this.api.put<DepositProduct>(`/deposit-products/${id}`, body);
  }

  deactivateDepositProduct(id: string): Observable<void> {
    return this.api.delete<void>(`/deposit-products/${id}`);
  }

  // Fixed Deposit Products
  listFixedDepositProducts(activeOnly = false): Observable<FixedDepositProduct[]> {
    return this.api.get<FixedDepositProduct[]>('/fixeddepositproducts', { activeOnly: String(activeOnly) });
  }
  getFixedDepositProduct(id: string): Observable<FixedDepositProduct> {
    return this.api.get<FixedDepositProduct>(`/fixeddepositproducts/${id}`);
  }
  createFixedDepositProduct(body: FixedDepositProductRequest): Observable<FixedDepositProduct> {
    return this.api.post<FixedDepositProduct>('/fixeddepositproducts', body);
  }
  updateFixedDepositProduct(id: string, body: FixedDepositProductRequest): Observable<FixedDepositProduct> {
    return this.api.put<FixedDepositProduct>(`/fixeddepositproducts/${id}`, body);
  }
  deactivateFixedDepositProduct(id: string): Observable<void> {
    return this.api.delete<void>(`/fixeddepositproducts/${id}`);
  }

  // Recurring Deposit Products
  listRecurringDepositProducts(activeOnly = false): Observable<RecurringDepositProduct[]> {
    return this.api.get<RecurringDepositProduct[]>('/recurringdepositproducts', { activeOnly: String(activeOnly) });
  }
  getRecurringDepositProduct(id: string): Observable<RecurringDepositProduct> {
    return this.api.get<RecurringDepositProduct>(`/recurringdepositproducts/${id}`);
  }
  createRecurringDepositProduct(body: RecurringDepositProductRequest): Observable<RecurringDepositProduct> {
    return this.api.post<RecurringDepositProduct>('/recurringdepositproducts', body);
  }
  updateRecurringDepositProduct(id: string, body: RecurringDepositProductRequest): Observable<RecurringDepositProduct> {
    return this.api.put<RecurringDepositProduct>(`/recurringdepositproducts/${id}`, body);
  }
  deactivateRecurringDepositProduct(id: string): Observable<void> {
    return this.api.delete<void>(`/recurringdepositproducts/${id}`);
  }

  // Share Products
  listShareProducts(activeOnly = false): Observable<ShareProduct[]> {
    return this.api.get<ShareProduct[]>('/shareproducts', { activeOnly: String(activeOnly) });
  }
  getShareProduct(id: string): Observable<ShareProduct> {
    return this.api.get<ShareProduct>(`/shareproducts/${id}`);
  }
  createShareProduct(body: ShareProductRequest): Observable<ShareProduct> {
    return this.api.post<ShareProduct>('/shareproducts', body);
  }
  updateShareProduct(id: string, body: ShareProductRequest): Observable<ShareProduct> {
    return this.api.put<ShareProduct>(`/shareproducts/${id}`, body);
  }
  deactivateShareProduct(id: string): Observable<void> {
    return this.api.delete<void>(`/shareproducts/${id}`);
  }
}
