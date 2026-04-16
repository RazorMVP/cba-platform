// web-react/src/app/features/accounting/api/types.ts

export type GlAccountType  = 'ASSET' | 'LIABILITY' | 'EQUITY' | 'INCOME' | 'EXPENSE'
export type GlAccountUsage = 'HEADER' | 'DETAIL'

export interface GlAccount {
  id: string
  glCode: string
  name: string
  accountType: GlAccountType
  usage: GlAccountUsage
  manualEntriesAllowed: boolean
  description?: string
  parentId?: string
  parentName?: string
  disabled: boolean
  tagId?: string
}

export interface GlAccountRequest {
  glCode: string
  name: string
  accountType: GlAccountType
  usage: GlAccountUsage
  manualEntriesAllowed: boolean
  description?: string
  parentId?: string
}

export type JournalEntryType      = 'DEBIT' | 'CREDIT'
export type JournalEntryCreatedBy = 'USER' | 'SYSTEM'

export interface JournalEntry {
  id: string
  transactionId: string
  entryDate: string
  glAccountId: string
  glAccountCode: string
  glAccountName: string
  type: JournalEntryType
  amount: number
  officeId?: string
  referenceNumber?: string
  comments?: string
  createdByType: JournalEntryCreatedBy
  reversed: boolean
  reversalId?: string
  entityType?: string
  entityId?: string
}

export interface ManualJournalLine {
  glAccountId: string
  amount: number
  comments?: string
}

export interface ManualJournalRequest {
  transactionDate: string
  locale: string
  dateFormat: string
  referenceNumber?: string
  comments?: string
  debits: ManualJournalLine[]
  credits: ManualJournalLine[]
}

export interface ProvisioningDefinition {
  id?: string
  categoryName: string
  minAge: number
  maxAge: number
  provisionPercentage: number
  liabilityAccountId: string
  liabilityAccountCode?: string
  liabilityAccountName?: string
  expenseAccountId: string
  expenseAccountCode?: string
  expenseAccountName?: string
}

export interface ProvisioningCriteria {
  id: string
  criteriaName: string
  createdBy?: string
  definitions: ProvisioningDefinition[]
}

export interface ProvisioningCriteriaRequest {
  criteriaName: string
  definitions: ProvisioningDefinition[]
}

export type FinancialActivityType =
  | 'ASSET_FUND_SOURCE' | 'ASSET_LOAN_PORTFOLIO' | 'ASSET_RECEIVABLE' | 'ASSET_OVERPAYMENT_LIABILITY'
  | 'LIABILITY_LINKED_TO_FLOAT' | 'LIABILITY_PAYMENT_GATEWAY' | 'LIABILITY_TRANSFER_IN_SUSPENSE'
  | 'INCOME_INTEREST' | 'INCOME_FEE' | 'EXPENSE_DEPRECIATION' | 'EXPENSE_LOAN_LOSSES'

export interface FinancialActivityAccount {
  id: string
  financialActivity: FinancialActivityType
  glAccountId: string
  glCode: string
  glAccountName: string
  glAccountType: GlAccountType
}

export interface FinancialActivityRequest {
  financialActivity: FinancialActivityType
  glAccountId: string
}
