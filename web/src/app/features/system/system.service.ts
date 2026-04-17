import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from '../../core/api/api.service';
import { PageResponse } from '../../core/models/api-response.model';

// ── Codes ─────────────────────────────────────────────────────────────────────
export interface Code {
  id: string;
  name: string;
  systemDefined: boolean;
  codeValues: CodeValue[];
}

export interface CodeValue {
  id: string;
  name: string;
  description: string;
  position: number;
  active: boolean;
}

export interface CreateCodeRequest {
  name: string;
}

export interface CreateCodeValueRequest {
  name: string;
  description: string;
  position: number;
}

// ── Global Configuration ──────────────────────────────────────────────────────
export interface GlobalConfig {
  id: string;
  name: string;
  enabled: boolean;
  stringValue: string | null;
  numericValue: number | null;
  booleanValue: boolean | null;
  description: string;
}

export interface UpdateConfigRequest {
  enabled: boolean;
  stringValue?: string;
  numericValue?: number;
  booleanValue?: boolean;
}

// ── Floating Rates ────────────────────────────────────────────────────────────
export interface FloatingRate {
  id: string;
  name: string;
  isActive: boolean;
  isBaseLendingRate: boolean;
  createdBy: string;
  createdOn: string;
  ratePeriods: FloatingRatePeriod[];
}

export interface FloatingRatePeriod {
  id: string;
  fromDate: string;
  interestRate: number;
  isDifferentialToBaseLendingRate: boolean;
}

export interface CreateFloatingRateRequest {
  name: string;
  isBaseLendingRate: boolean;
  ratePeriods: Omit<FloatingRatePeriod, 'id'>[];
}

// ── Taxes ─────────────────────────────────────────────────────────────────────
export interface TaxComponent {
  id: string;
  name: string;
  percentage: number;
  startDate: string;
  creditAccountId: string | null;
  debitAccountId: string | null;
}

export interface TaxGroup {
  id: string;
  name: string;
  components: TaxGroupMapping[];
}

export interface TaxGroupMapping {
  taxComponentId: string;
  taxComponentName: string;
  startDate: string;
}

export interface CreateTaxComponentRequest {
  name: string;
  percentage: number;
  startDate: string;
  creditAccountId?: string;
  debitAccountId?: string;
}

export interface CreateTaxGroupRequest {
  name: string;
  components: { taxComponentId: string; startDate: string }[];
}

// ── Holidays ──────────────────────────────────────────────────────────────

export type RepaymentSchedulingType =
  'SAME_DAY' | 'NEXT_WORKING_DAY' | 'PREVIOUS_WORKING_DAY' | 'NEXT_REPAYMENT_MEETING_DATE';

export interface Holiday {
  id: string;
  name: string;
  fromDate: string;
  toDate: string;
  repaymentSchedulingType: RepaymentSchedulingType;
  rescheduledRepaymentDate?: string;
  status: 'PENDING' | 'ACTIVE';
  processed: boolean;
  createdAt: string;
}

export interface CreateHolidayRequest {
  name: string;
  fromDate: string;
  toDate: string;
  repaymentSchedulingType: RepaymentSchedulingType;
  rescheduledRepaymentDate?: string;
}

// ── Account Number Algorithms ─────────────────────────────────────────────
export interface TenantAlgorithmConfig {
  bankCode:       string | null;
  validationMode: 'STRICT' | 'PARANOID';
  algorithms:     Record<string, string>;
}

export interface UpdateAlgorithmConfigRequest {
  bankCode:       string;
  validationMode: 'STRICT' | 'PARANOID';
  algorithms:     Record<string, string>;
}

@Injectable({ providedIn: 'root' })
export class SystemService {
  private readonly api = inject(ApiService);

  // ── Codes ──────────────────────────────────────────────────────────────────
  listCodes(): Observable<Code[]> {
    return this.api.get<Code[]>('/codes');
  }

  createCode(req: CreateCodeRequest): Observable<Code> {
    return this.api.post<Code>('/codes', req);
  }

  deleteCode(id: string): Observable<void> {
    return this.api.delete<void>(`/codes/${id}`);
  }

  listCodeValues(codeId: string): Observable<CodeValue[]> {
    return this.api.get<CodeValue[]>(`/codes/${codeId}/codevalues`);
  }

  createCodeValue(codeId: string, req: CreateCodeValueRequest): Observable<CodeValue> {
    return this.api.post<CodeValue>(`/codes/${codeId}/codevalues`, req);
  }

  updateCodeValue(codeId: string, valueId: string, req: CreateCodeValueRequest): Observable<CodeValue> {
    return this.api.put<CodeValue>(`/codes/${codeId}/codevalues/${valueId}`, req);
  }

  deleteCodeValue(codeId: string, valueId: string): Observable<void> {
    return this.api.delete<void>(`/codes/${codeId}/codevalues/${valueId}`);
  }

  // ── Global Configuration ───────────────────────────────────────────────────
  listConfigurations(): Observable<GlobalConfig[]> {
    return this.api.get<GlobalConfig[]>('/configurations');
  }

  updateConfiguration(id: string, req: UpdateConfigRequest): Observable<GlobalConfig> {
    return this.api.put<GlobalConfig>(`/configurations/${id}`, req);
  }

  // ── Floating Rates ─────────────────────────────────────────────────────────
  listFloatingRates(): Observable<FloatingRate[]> {
    return this.api.get<FloatingRate[]>('/floatingrates');
  }

  createFloatingRate(req: CreateFloatingRateRequest): Observable<FloatingRate> {
    return this.api.post<FloatingRate>('/floatingrates', req);
  }

  updateFloatingRate(id: string, req: CreateFloatingRateRequest): Observable<FloatingRate> {
    return this.api.put<FloatingRate>(`/floatingrates/${id}`, req);
  }

  deleteFloatingRate(id: string): Observable<void> {
    return this.api.delete<void>(`/floatingrates/${id}`);
  }

  // ── Taxes ──────────────────────────────────────────────────────────────────
  listTaxComponents(): Observable<TaxComponent[]> {
    return this.api.get<TaxComponent[]>('/taxes/components');
  }

  createTaxComponent(req: CreateTaxComponentRequest): Observable<TaxComponent> {
    return this.api.post<TaxComponent>('/taxes/components', req);
  }

  updateTaxComponent(id: string, req: CreateTaxComponentRequest): Observable<TaxComponent> {
    return this.api.put<TaxComponent>(`/taxes/components/${id}`, req);
  }

  listTaxGroups(): Observable<TaxGroup[]> {
    return this.api.get<TaxGroup[]>('/taxes/groups');
  }

  createTaxGroup(req: CreateTaxGroupRequest): Observable<TaxGroup> {
    return this.api.post<TaxGroup>('/taxes/groups', req);
  }

  updateTaxGroup(id: string, req: CreateTaxGroupRequest): Observable<TaxGroup> {
    return this.api.put<TaxGroup>(`/taxes/groups/${id}`, req);
  }

  // ── Account Number Algorithms ─────────────────────────────────────────────
  getAlgorithmConfig(tenantId: string): Observable<TenantAlgorithmConfig> {
    return this.api.get<TenantAlgorithmConfig>(`/tenants/${tenantId}/account-algorithm`);
  }

  updateAlgorithmConfig(tenantId: string, req: UpdateAlgorithmConfigRequest): Observable<TenantAlgorithmConfig> {
    return this.api.put<TenantAlgorithmConfig>(`/tenants/${tenantId}/account-algorithm`, req);
  }

  // ── Holidays ──────────────────────────────────────────────────────────────
  listHolidays(page = 0): Observable<PageResponse<Holiday>> {
    return this.api.getPage<Holiday>('/holidays', page, 20);
  }

  createHoliday(req: CreateHolidayRequest): Observable<Holiday> {
    return this.api.post<Holiday>('/holidays', req);
  }

  activateHoliday(id: string): Observable<Holiday> {
    return this.api.post<Holiday>(`/holidays/${id}/activate`, {});
  }

  deleteHoliday(id: string): Observable<void> {
    return this.api.delete<void>(`/holidays/${id}`);
  }
}
