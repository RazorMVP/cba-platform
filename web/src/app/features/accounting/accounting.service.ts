import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from '../../core/api/api.service';
import { PageResponse } from '../../core/models/api-response.model';

// ── Financial Activity Accounts ────────────────────────────────────────────────

/** Must match the backend enum FinancialActivityAccount.FinancialActivity exactly. */
export type FinancialActivityType =
  | 'ASSET_FUND_SOURCE' | 'ASSET_CASH_AT_TELLER' | 'ASSET_INTEREST_RECEIVABLE' | 'ASSET_LOAN_PORTFOLIO'
  | 'ASSET_OVERDRAFT_PORTFOLIO' | 'ASSET_FX_POSITION' | 'ASSET_FX_POSITION_EQUIVALENT'
  | 'LIABILITY_SAVINGS_CONTROL' | 'LIABILITY_TRANSFER_IN_SUSPENSE'
  | 'INCOME_INTEREST' | 'INCOME_FEES' | 'INCOME_FX_GAIN_LOSS'
  | 'EXPENSE_LOAN_LOSS_PROVISION' | 'EXPENSE_WRITE_OFF' | 'EXPENSE_INTEREST_ON_SAVINGS'
  | 'EXPENSE_CASH_OVER_SHORT'
  | 'EQUITY_MIGRATION_CLEARING';

export interface FinancialActivityAccount {
  id: string;
  financialActivity: FinancialActivityType;
  glAccountId: string;
  glCode: string;
  glAccountName: string;
  glAccountType: GlAccountType;
}

export interface FinancialActivityRequest {
  financialActivity: FinancialActivityType;
  glAccountId: string;
}

// ── GL Accounts ───────────────────────────────────────────────────────────────

export type GlAccountType  = 'ASSET' | 'LIABILITY' | 'EQUITY' | 'INCOME' | 'EXPENSE';
export type GlAccountUsage = 'HEADER' | 'DETAIL';

export interface GlAccount {
  id: string;
  glCode: string;
  name: string;
  accountType: GlAccountType;
  usage: GlAccountUsage;
  manualEntriesAllowed: boolean;
  description?: string;
  parentId?: string;
  parentName?: string;
  disabled: boolean;
  tagId?: string;
}

export interface GlAccountRequest {
  glCode: string;
  name: string;
  accountType: GlAccountType;
  usage: GlAccountUsage;
  manualEntriesAllowed: boolean;
  description?: string;
  parentId?: string;
  tagId?: string;
}

// ── Journal Entries ────────────────────────────────────────────────────────────

export type JournalEntryType = 'DEBIT' | 'CREDIT';

/** One journal line, as GET /journalentries returns it (backend JournalEntryResponse). */
export interface JournalEntry {
  id: string;
  transactionId: string;
  transactionDate: string;
  postedAt: string;
  glAccountId: string;
  glAccountCode: string;
  glAccountName: string;
  entryType: JournalEntryType;
  amount: number;
  currencyCode: string;
  positionCurrency?: string | null;
  entityType?: string | null;
  entityId?: string | null;
  referenceNumber?: string | null;
  description?: string | null;
  /** Created in the GL (a manual journal): the only kind the GL can reverse. */
  manual: boolean;
  reversed: boolean;
  /** Set on reversal lines: the line this one reverses. */
  reversalOfId?: string | null;
}

export interface ManualJournalLine {
  glCode: string;
  amount: number;
  description?: string;
}

/** A manual journal in one currency; any number of lines, debits must equal credits. */
export interface ManualJournalRequest {
  transactionDate: string;
  currencyCode: string;
  comments?: string;
  referenceNumber?: string;
  debits: ManualJournalLine[];
  credits: ManualJournalLine[];
}

export interface JournalReversal {
  reversedTransactionId: string;
  reversalTransactionId: string;
  lines: JournalEntry[];
}

// ── GL Closures ────────────────────────────────────────────────────────────────

export interface GlClosure {
  id: string;
  officeId: string;
  officeName: string;
  closingDate: string;
  closedBy?: string;
  comments?: string;
}

export interface GlClosureRequest {
  officeId: string;
  closingDate: string;
  comments?: string;
}

// ── Provisioning Criteria ──────────────────────────────────────────────────────

export interface ProvisioningDefinition {
  id?: string;
  categoryName: string;
  minAge: number;
  maxAge: number;
  provisionPercentage: number;
  liabilityAccountId: string;
  liabilityAccountCode?: string;
  liabilityAccountName?: string;
  expenseAccountId: string;
  expenseAccountCode?: string;
  expenseAccountName?: string;
}

export interface ProvisioningCriteria {
  id: string;
  criteriaName: string;
  createdBy?: string;
  definitions: ProvisioningDefinition[];
}

export interface ProvisioningCriteriaRequest {
  criteriaName: string;
  definitions: ProvisioningDefinition[];
}

// ── Trial Balance ─────────────────────────────────────────────────────────────

/** One GL account in one currency; balances are debit-positive. */
export interface TrialBalanceRow {
  glCode: string;
  accountName: string;
  accountType: GlAccountType;
  currencyCode: string;
  openingBalance: number;
  debitMovement: number;
  creditMovement: number;
  closingBalance: number;
}

/** Totals for one currency: double entry balances within a currency, never across them. */
export interface TrialBalanceCurrencyTotals {
  currencyCode: string;
  totalDebitMovement: number;
  totalCreditMovement: number;
  totalClosingDebit: number;
  totalClosingCredit: number;
  balanced: boolean;
}

export interface TrialBalanceResponse {
  fromDate: string;
  toDate: string;
  rows: TrialBalanceRow[];
  currencies: TrialBalanceCurrencyTotals[];
  /** True when every currency balances. */
  balanced: boolean;
}

// ── Accounting Rules ──────────────────────────────────────────────────────────
export interface AccountingRule {
  id: string;
  name: string;
  description: string | null;
  debitAccountId: string;
  debitAccountCode?: string;
  debitAccountName?: string;
  creditAccountId: string;
  creditAccountCode?: string;
  creditAccountName?: string;
  allowMultipleDebits: boolean;
  allowMultipleCredits: boolean;
  active: boolean;
}

export interface CreateAccountingRuleRequest {
  name: string;
  description: string;
  debitAccountId: string;
  creditAccountId: string;
  allowMultipleDebits: boolean;
  allowMultipleCredits: boolean;
  active: boolean;
}

// ── Service ────────────────────────────────────────────────────────────────────

@Injectable({ providedIn: 'root' })
export class AccountingService {
  private readonly api = inject(ApiService);

  // Financial Activity Accounts
  listFinancialActivityAccounts(): Observable<FinancialActivityAccount[]> {
    return this.api.get<FinancialActivityAccount[]>('/financialactivityaccounts');
  }
  createFinancialActivityAccount(req: FinancialActivityRequest): Observable<FinancialActivityAccount> {
    return this.api.post<FinancialActivityAccount>('/financialactivityaccounts', req);
  }
  updateFinancialActivityAccount(id: string, req: FinancialActivityRequest): Observable<FinancialActivityAccount> {
    return this.api.put<FinancialActivityAccount>(`/financialactivityaccounts/${id}`, req);
  }
  deleteFinancialActivityAccount(id: string): Observable<void> {
    return this.api.delete<void>(`/financialactivityaccounts/${id}`);
  }

  // GL Accounts
  listGlAccounts(params?: Record<string, string>): Observable<GlAccount[]> {
    return this.api.get<GlAccount[]>('/glaccounts', params);
  }
  getGlAccount(id: string): Observable<GlAccount> {
    return this.api.get<GlAccount>(`/glaccounts/${id}`);
  }
  createGlAccount(req: GlAccountRequest): Observable<GlAccount> {
    return this.api.post<GlAccount>('/glaccounts', req);
  }
  updateGlAccount(id: string, req: GlAccountRequest): Observable<GlAccount> {
    return this.api.put<GlAccount>(`/glaccounts/${id}`, req);
  }
  disableGlAccount(id: string): Observable<GlAccount> {
    return this.api.command<GlAccount>(`/glaccounts/${id}`, 'disable');
  }
  enableGlAccount(id: string): Observable<GlAccount> {
    return this.api.command<GlAccount>(`/glaccounts/${id}`, 'enable');
  }

  // Journal Entries
  /** Every journal line dated between from and to (ISO dates, both required by the API). */
  listJournalEntries(from: string, to: string): Observable<JournalEntry[]> {
    return this.api.get<JournalEntry[]>('/journalentries', { from, to });
  }
  createManualJournalEntry(req: ManualJournalRequest): Observable<JournalEntry[]> {
    return this.api.post<JournalEntry[]>('/journalentries', req);
  }
  /** Reverses the whole manual journal the line belongs to. */
  reverseJournalEntry(id: string): Observable<JournalReversal> {
    return this.api.post<JournalReversal>(`/journalentries/${id}/reverse`, {});
  }

  // GL Closures
  listClosures(officeId: string): Observable<GlClosure[]> {
    return this.api.get<GlClosure[]>('/glclosures', { officeId });
  }
  createClosure(req: GlClosureRequest): Observable<GlClosure> {
    const params: Record<string, string> = { officeId: req.officeId, closingDate: req.closingDate };
    if (req.comments) params['comments'] = req.comments;
    return this.api.postParams<GlClosure>('/glclosures', params);
  }

  // Provisioning
  listProvisioningCriteria(): Observable<ProvisioningCriteria[]> {
    return this.api.get<ProvisioningCriteria[]>('/provisioningcriteria');
  }
  getProvisioningCriteria(id: string): Observable<ProvisioningCriteria> {
    return this.api.get<ProvisioningCriteria>(`/provisioningcriteria/${id}`);
  }
  createProvisioningCriteria(req: ProvisioningCriteriaRequest): Observable<ProvisioningCriteria> {
    return this.api.post<ProvisioningCriteria>('/provisioningcriteria', req);
  }
  updateProvisioningCriteria(id: string, req: ProvisioningCriteriaRequest): Observable<ProvisioningCriteria> {
    return this.api.put<ProvisioningCriteria>(`/provisioningcriteria/${id}`, req);
  }
  deleteProvisioningCriteria(id: string): Observable<void> {
    return this.api.delete<void>(`/provisioningcriteria/${id}`);
  }

  // Trial Balance
  getTrialBalance(fromDate: string, toDate: string): Observable<TrialBalanceResponse> {
    return this.api.get<TrialBalanceResponse>('/accounting/trial-balance', { fromDate, toDate });
  }

  // Accounting Rules
  listAccountingRules(page = 0): Observable<PageResponse<AccountingRule>> {
    return this.api.getPage<AccountingRule>('/accountingrules', page, 20);
  }
  createAccountingRule(req: CreateAccountingRuleRequest): Observable<AccountingRule> {
    return this.api.post<AccountingRule>('/accountingrules', req);
  }
  updateAccountingRule(id: string, req: CreateAccountingRuleRequest): Observable<AccountingRule> {
    return this.api.put<AccountingRule>(`/accountingrules/${id}`, req);
  }
  deleteAccountingRule(id: string): Observable<void> {
    return this.api.delete<void>(`/accountingrules/${id}`);
  }
}
