// web-react/src/app/features/products/api/types.ts

export interface PageMeta { page: number; size: number; total: number }
export interface ApiResponse<T> { data: T; meta?: PageMeta; errors?: { code: string; message: string }[] }

// ── Shared refs ─────────────────────────────────────────────────────────────

export interface GlAccountRef { id: string; glCode: string; name: string }
export interface ChargeRef { id: string; name: string; chargeCalculationType: string; amount: number; currencyCode: string }

// ── Loan Product ─────────────────────────────────────────────────────────────

export type RepaymentFrequencyType = 'DAYS' | 'WEEKS' | 'MONTHS'
export type InterestType = 'DECLINING_BALANCE' | 'FLAT'
export type AmortizationType = 'EQUAL_INSTALLMENTS' | 'EQUAL_PRINCIPAL'
export type InterestRateFrequencyType = 'PER_YEAR' | 'PER_MONTH' | 'PER_WEEK'
export type DaysInYearType = '360' | '365' | 'ACTUAL'

export interface LoanProduct {
  id: string
  name: string
  shortName: string
  description?: string
  active: boolean
  currencyCode: string
  minPrincipalAmount: number
  defaultPrincipalAmount: number
  maxPrincipalAmount: number
  minInterestRatePerPeriod?: number
  defaultInterestRatePerPeriod: number
  maxInterestRatePerPeriod?: number
  interestRateFrequencyType: InterestRateFrequencyType
  interestType: InterestType
  amortizationType: AmortizationType
  repaymentEvery: number
  repaymentFrequencyType: RepaymentFrequencyType
  numberOfRepayments: number
  graceOnPrincipalPayment?: number
  graceOnInterestPayment?: number
  inArrearsTolerance?: number
  daysInYearType?: DaysInYearType
  // GL Accounts
  fundSourceAccount?: GlAccountRef
  loanPortfolioAccount?: GlAccountRef
  interestOnLoanAccount?: GlAccountRef
  incomeFromFeeAccount?: GlAccountRef
  incomeFromPenaltyAccount?: GlAccountRef
  interestReceivableAccount?: GlAccountRef
  feeReceivableAccount?: GlAccountRef
  penaltyReceivableAccount?: GlAccountRef
  // Charges
  charges?: ChargeRef[]
  createdAt: string
}

export interface CreateLoanProductRequest {
  name: string
  shortName: string
  description?: string
  currencyCode: string
  minPrincipalAmount: number
  defaultPrincipalAmount: number
  maxPrincipalAmount: number
  defaultInterestRatePerPeriod: number
  minInterestRatePerPeriod?: number
  maxInterestRatePerPeriod?: number
  interestRateFrequencyType: InterestRateFrequencyType
  interestType: InterestType
  amortizationType: AmortizationType
  repaymentEvery: number
  repaymentFrequencyType: RepaymentFrequencyType
  numberOfRepayments: number
  graceOnPrincipalPayment?: number
  graceOnInterestPayment?: number
}

// ── Deposit Product ──────────────────────────────────────────────────────────

export type DepositAccountType = 'SAVINGS' | 'CHECKING'
export type InterestCompoundingPeriodType = 'DAILY' | 'MONTHLY' | 'QUARTERLY' | 'ANNUALLY'

export interface DepositProduct {
  id: string
  name: string
  shortName: string
  description?: string
  active: boolean
  currencyCode: string
  accountType: DepositAccountType
  minimumBalance?: number
  minRequiredOpeningBalance?: number
  nominalAnnualInterestRate: number
  interestCompoundingPeriodType: InterestCompoundingPeriodType
  interestPostingPeriodType: InterestCompoundingPeriodType
  daysInYearType?: DaysInYearType
  lockinPeriodFrequency?: number
  lockinPeriodFrequencyType?: RepaymentFrequencyType
  withdrawalFeeForTransfers: boolean
  allowOverdraft: boolean
  overdraftLimit?: number
  overdraftInterestRate?: number
  minOverdraftForInterestCalculation?: number
  charges?: ChargeRef[]
  createdAt: string
}

export interface CreateDepositProductRequest {
  name: string
  shortName: string
  description?: string
  currencyCode: string
  accountType: DepositAccountType
  minimumBalance?: number
  minRequiredOpeningBalance?: number
  nominalAnnualInterestRate: number
  interestCompoundingPeriodType: InterestCompoundingPeriodType
  interestPostingPeriodType: InterestCompoundingPeriodType
  withdrawalFeeForTransfers?: boolean
  allowOverdraft?: boolean
  overdraftLimit?: number
  overdraftInterestRate?: number
}

// ── Fixed Deposit Product ────────────────────────────────────────────────────

export interface FixedDepositProduct {
  id: string
  name: string
  shortName: string
  description?: string
  active: boolean
  currencyCode: string
  nominalAnnualInterestRate: number
  minDepositAmount: number
  maxDepositAmount?: number
  minDepositTerm: number
  maxDepositTerm?: number
  penaltyInterestRate?: number
  createdAt: string
}

export interface CreateFixedDepositProductRequest {
  name: string
  shortName: string
  description?: string
  currencyCode: string
  nominalAnnualInterestRate: number
  minDepositAmount: number
  maxDepositAmount?: number
  minDepositTerm: number
  maxDepositTerm?: number
  penaltyInterestRate?: number
}

// ── Recurring Deposit Product ────────────────────────────────────────────────

export interface RecurringDepositProduct {
  id: string
  name: string
  shortName: string
  description?: string
  active: boolean
  currencyCode: string
  nominalAnnualInterestRate: number
  minDepositAmount: number
  recurringDepositFrequency: number
  recurringDepositFrequencyType: RepaymentFrequencyType
  minDepositTerm?: number
  maxDepositTerm?: number
  penaltyInterestRate?: number
  createdAt: string
}

export interface CreateRecurringDepositProductRequest {
  name: string
  shortName: string
  description?: string
  currencyCode: string
  nominalAnnualInterestRate: number
  minDepositAmount: number
  recurringDepositFrequency: number
  recurringDepositFrequencyType: RepaymentFrequencyType
  minDepositTerm?: number
  maxDepositTerm?: number
  penaltyInterestRate?: number
}

// ── Share Product ────────────────────────────────────────────────────────────

export interface ShareProduct {
  id: string
  name: string
  shortName: string
  description?: string
  active: boolean
  currencyCode: string
  unitPrice: number
  sharesIssued: number
  minimumShares: number
  maximumShares?: number
  lockinPeriodFrequency?: number
  lockinPeriodFrequencyType?: RepaymentFrequencyType
  dividendPolicy?: string
  createdAt: string
}

export interface CreateShareProductRequest {
  name: string
  shortName: string
  description?: string
  currencyCode: string
  unitPrice: number
  minimumShares: number
  maximumShares?: number
}
