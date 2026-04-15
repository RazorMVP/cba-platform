// web-react/src/app/features/operations/api/types.ts
// Shared domain types for the Operations module — mirror backend DTOs

export type KycStatus =
  | 'PENDING_KYC'
  | 'ACTIVE'
  | 'SUSPENDED'
  | 'CLOSED'
  | 'REJECTED'
  | 'WITHDRAWN'
  | 'TRANSFER_IN_PROGRESS'

export interface Customer {
  id: string
  externalId?: string
  firstName: string
  lastName: string
  email: string
  phone: string
  kycStatus: KycStatus
  createdAt: string
  officeId?: string
  officeName?: string
  staffId?: string
  staffName?: string
}

export type AccountType = 'SAVINGS' | 'CHECKING' | 'FIXED_DEPOSIT'
export type AccountStatus = 'ACTIVE' | 'INACTIVE' | 'CLOSED' | 'FROZEN'

export interface Account {
  id: string
  accountNumber: string
  customerId: string
  customerName: string
  accountType: AccountType
  status: AccountStatus
  balance: number
  currencyCode: string
  productId: string
  productName: string
  createdAt: string
}

export interface Transaction {
  id: string
  accountId: string
  transactionType: string
  amount: number
  runningBalance: number
  referenceNumber: string
  description?: string
  createdAt: string
}

export type LoanStatus =
  | 'SUBMITTED'
  | 'UNDER_REVIEW'
  | 'APPROVED'
  | 'DISBURSED'
  | 'ACTIVE'
  | 'IN_ARREARS'
  | 'CLOSED_OBLIGATIONS_MET'
  | 'WRITTEN_OFF'
  | 'REJECTED'

export interface Loan {
  id: string
  loanAccountNumber: string
  customerId: string
  customerName: string
  productId: string
  productName: string
  principalAmount: number
  outstandingBalance: number
  status: LoanStatus
  disbursementDate?: string
  maturityDate?: string
  interestRate: number
  termMonths: number
  currencyCode: string
  createdAt: string
}

export interface RepaymentScheduleItem {
  id: string
  loanId: string
  dueDate: string
  principalDue: number
  interestDue: number
  totalDue: number
  principalPaid: number
  interestPaid: number
  status: 'PENDING' | 'PAID' | 'PARTIAL' | 'OVERDUE'
}

export type PaymentStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED' | 'REVERSED'
export type PaymentType = 'INTERNAL_TRANSFER' | 'EXTERNAL' | 'STANDING_ORDER' | 'BILL_PAYMENT'

export interface Payment {
  id: string
  sourceAccountId: string
  sourceAccountNumber: string
  destinationAccountId?: string
  destinationAccountNumber?: string
  amount: number
  currencyCode: string
  status: PaymentStatus
  paymentType: PaymentType
  description?: string
  referenceNumber: string
  isCrossCurrency: boolean
  sourceAmount?: number
  sourceCurrency?: string
  destinationAmount?: number
  destinationCurrency?: string
  exchangeRateUsed?: number
  createdAt: string
}

export type TellerStatus = 'INACTIVE' | 'ACTIVE' | 'CLOSED'

export interface Teller {
  id: string
  name: string
  officeId: string
  officeName: string
  status: TellerStatus
  description?: string
  createdAt: string
}

export interface Cashier {
  id: string
  tellerId: string
  staffId: string
  staffName: string
  isFullDay: boolean
  startTime?: string
  endTime?: string
}

export type SessionStatus = 'OPEN' | 'CLOSED'

export interface TellerSession {
  id: string
  cashierId: string
  cashierName: string
  sessionDate: string
  status: SessionStatus
  openingBalance: number
  closingBalance?: number
  actualCash?: number
  difference?: number
  currencyCode: string
}

export interface PageMeta {
  page: number
  size: number
  total: number
}

export interface ApiResponse<T> {
  data: T
  meta: PageMeta
  errors: { code: string; message: string }[]
}
