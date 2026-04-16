import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from '../../core/api/api.service';
import { PageResponse } from '../../core/models/api-response.model';

// ── Financial Activity Accounts ────────────────────────────────────────────────

export type FinancialActivityType =
  | 'ASSET_FUND_SOURCE' | 'ASSET_LOAN_PORTFOLIO' | 'ASSET_RECEIVABLE' | 'ASSET_OVERPAYMENT_LIABILITY'
  | 'LIABILITY_LINKED_TO_FLOAT' | 'LIABILITY_PAYMENT_GATEWAY' | 'LIABILITY_TRANSFER_IN_SUSPENSE'
  | 'INCOME_INTEREST' | 'INCOME_FEE' | 'EXPENSE_DEPRECIATION' | 'EXPENSE_LOAN_LOSSES';

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

export type JournalEntryType        = 'DEBIT' | 'CREDIT';
export type JournalEntryCreatedBy   = 'USER' | 'SYSTEM';

export interface JournalEntry {
  id: string;
  transactionId: string;
  entryDate: string;
  glAccountId: string;
  glAccountCode: string;
  glAccountName: string;
  type: JournalEntryType;
  amount: number;
  officeId?: string;
  referenceNumber?: string;
  comments?: string;
  createdByType: JournalEntryCreatedBy;
  reversed: boolean;
  reversalId?: string;
  entityType?: string;
  entityId?: string;
}

export interface ManualJournalLine {
  glAccountId: string;
  amount: number;
  comments?: string;
}

export interface ManualJournalRequest {
  transactionDate: string;
  locale: string;
  dateFormat: string;
  referenceNumber?: string;
  comments?: string;
  debits: ManualJournalLine[];
  credits: ManualJournalLine[];
}

// ── GL Closures ────────────────────────────────────────────────────────────────

export interface GlClosure {
  id: string;
  officeId?: string;
  officeName?: string;
  openingDate: string;
  closingDate: string;
  comments?: string;
  createdDate: string;
  deletedDate?: string;
}

export interface GlClosureRequest {
  openingDate: string;
  closingDate: string;
  locale: string;
  dateFormat: string;
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

// ── Service ────────────────────────────────────────────────────────────────────

@Injectable({ providedIn: 'root' })
export class AccountingService {
  private readonly api = inject(ApiService);

  // Financial Activity Accounts
  listFinancialActivityAccounts(): Observable<FinancialActivityAccount[]> {
    return this.api.get<FinancialActivityAccount[]>('/api/v1/financialactivityaccounts');
  }
  createFinancialActivityAccount(req: FinancialActivityRequest): Observable<FinancialActivityAccount> {
    return this.api.post<FinancialActivityAccount>('/api/v1/financialactivityaccounts', req);
  }
  updateFinancialActivityAccount(id: string, req: FinancialActivityRequest): Observable<FinancialActivityAccount> {
    return this.api.put<FinancialActivityAccount>(`/api/v1/financialactivityaccounts/${id}`, req);
  }
  deleteFinancialActivityAccount(id: string): Observable<void> {
    return this.api.delete<void>(`/api/v1/financialactivityaccounts/${id}`);
  }

  // GL Accounts
  listGlAccounts(params?: Record<string, string>): Observable<GlAccount[]> {
    return this.api.get<GlAccount[]>('/api/v1/glaccounts', params);
  }
  getGlAccount(id: string): Observable<GlAccount> {
    return this.api.get<GlAccount>(`/api/v1/glaccounts/${id}`);
  }
  createGlAccount(req: GlAccountRequest): Observable<GlAccount> {
    return this.api.post<GlAccount>('/api/v1/glaccounts', req);
  }
  updateGlAccount(id: string, req: GlAccountRequest): Observable<GlAccount> {
    return this.api.put<GlAccount>(`/api/v1/glaccounts/${id}`, req);
  }
  disableGlAccount(id: string): Observable<GlAccount> {
    return this.api.command<GlAccount>(`/api/v1/glaccounts/${id}`, 'disable');
  }
  enableGlAccount(id: string): Observable<GlAccount> {
    return this.api.command<GlAccount>(`/api/v1/glaccounts/${id}`, 'enable');
  }

  // Journal Entries
  listJournalEntries(params?: Record<string, string>): Observable<PageResponse<JournalEntry>> {
    return this.api.getPage<JournalEntry>('/api/v1/journalentries', 0, 50, params);
  }
  createManualJournalEntry(req: ManualJournalRequest): Observable<{ transactionId: string }> {
    return this.api.post<{ transactionId: string }>('/api/v1/journalentries', req);
  }
  reverseJournalEntry(id: string): Observable<{ transactionId: string }> {
    return this.api.post<{ transactionId: string }>(`/api/v1/journalentries/${id}/reverse`, {});
  }

  // GL Closures
  listClosures(): Observable<GlClosure[]> {
    return this.api.get<GlClosure[]>('/api/v1/glclosures');
  }
  createClosure(req: GlClosureRequest): Observable<GlClosure> {
    return this.api.post<GlClosure>('/api/v1/glclosures', req);
  }
  deleteClosure(id: string): Observable<void> {
    return this.api.delete<void>(`/api/v1/glclosures/${id}`);
  }

  // Provisioning
  listProvisioningCriteria(): Observable<ProvisioningCriteria[]> {
    return this.api.get<ProvisioningCriteria[]>('/api/v1/provisioningcriteria');
  }
  getProvisioningCriteria(id: string): Observable<ProvisioningCriteria> {
    return this.api.get<ProvisioningCriteria>(`/api/v1/provisioningcriteria/${id}`);
  }
  createProvisioningCriteria(req: ProvisioningCriteriaRequest): Observable<ProvisioningCriteria> {
    return this.api.post<ProvisioningCriteria>('/api/v1/provisioningcriteria', req);
  }
  updateProvisioningCriteria(id: string, req: ProvisioningCriteriaRequest): Observable<ProvisioningCriteria> {
    return this.api.put<ProvisioningCriteria>(`/api/v1/provisioningcriteria/${id}`, req);
  }
  deleteProvisioningCriteria(id: string): Observable<void> {
    return this.api.delete<void>(`/api/v1/provisioningcriteria/${id}`);
  }
}
