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

// ── Payment Types ─────────────────────────────────────────────────────────
export interface SystemPaymentType {
  id: string;
  name: string;
  description: string | null;
  cashPayment: boolean;
  position: number | null;
  systemDefined: boolean;
}

export interface CreatePaymentTypeRequest {
  name: string;
  description: string;
  cashPayment: boolean;
  position: number;
}

// ── Funds ─────────────────────────────────────────────────────────────────
export interface Fund {
  id: string;
  name: string;
  externalId: string | null;
}

export interface CreateFundRequest {
  name: string;
  externalId: string;
}

// ── Account Number Formats ────────────────────────────────────────────────
export type AccountType = 'LOAN' | 'SAVINGS' | 'CLIENT' | 'SHARE';
export type PrefixType  = 'NONE' | 'ACCOUNT_TYPE' | 'OFFICE_NAME' | 'LOAN_PRODUCT_SHORT_NAME' | 'CLIENT_NAME';

export interface AccountNumberFormat {
  id: string;
  accountType: AccountType;
  prefixType:  PrefixType;
}

export interface CreateAccountNumberFormatRequest {
  accountType: AccountType;
  prefixType:  PrefixType;
}

// ── DataTables ────────────────────────────────────────────────────────────
export interface DataTableColumn {
  columnName:   string;
  columnType:   string;
  columnLength: number | null;
  nullable:     boolean;
  unique:       boolean;
  codeId:       string | null;
}

export interface DataTable {
  registeredTableName:  string;
  applicationTableName: string;
  allowMultipleRows:    boolean;
  columns:              DataTableColumn[];
}

export interface CreateDataTableRequest {
  registeredTableName:  string;
  applicationTableName: string;
  allowMultipleRows:    boolean;
  columns: { columnName: string; columnType: string; columnLength: number | null; nullable: boolean; unique: boolean }[];
}

// ── Surveys ────────────────────────────────────────────────────────────────
export interface SurveyResponseOption {
  id: string;
  value: string;
  score: number;
  sequenceNo: number;
}

export interface SurveyQuestion {
  id: string;
  key: string;
  text: string;
  sequenceNo: number;
  responses: SurveyResponseOption[];
}

export interface Survey {
  id: string;
  name: string;
  key: string;
  countryCode: string;
  description: string | null;
  questions: SurveyQuestion[];
}

export interface CreateSurveyRequest {
  name: string;
  key: string;
  countryCode: string;
  description: string;
}

// ── Credit Bureau ─────────────────────────────────────────────────────────
export interface CreditBureau {
  id: string;
  name: string;
  country: string;
  implClass: string;
  active: boolean;
  description: string | null;
}

export interface CreditBureauMapping {
  id: string;
  creditBureauId: string;
  loanProductId: string;
  loanProductName: string;
  creditCheckMandatory: boolean;
}

export interface CreateCreditBureauRequest {
  name: string;
  country: string;
  implClass: string;
  description: string;
}

export interface CreateCreditBureauMappingRequest {
  loanProductId: string;
  creditCheckMandatory: boolean;
}

// ── Exchange Rates ────────────────────────────────────────────────────────
export interface ExchangeRateResponse {
  id: string;
  fromCurrency: string;
  toCurrency: string;
  rate: number;
  inverseRate: number;
  active: boolean;
  updatedAt: string;
}

export interface ExchangeRateRequest {
  fromCurrency: string;
  toCurrency: string;
  rate: number;
}

// ── Field Configuration ───────────────────────────────────────────────────
export interface FieldConfiguration {
  id: string;
  entityType: string;
  fieldName: string;
  fieldLabel: string;
  enabled: boolean;
  mandatory: boolean;
  displayOrder: number;
  description: string | null;
  updatedAt: string;
}

export interface UpdateFieldConfigRequest {
  fieldLabel?: string;
  enabled?: boolean;
  mandatory?: boolean;
  displayOrder?: number;
  description?: string;
}

export interface CreateFieldConfigRequest {
  entityType: string;
  fieldName: string;
  fieldLabel: string;
  enabled?: boolean;
  mandatory?: boolean;
  displayOrder?: number;
  description?: string;
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

  // ── Payment Types ──────────────────────────────────────────────────────────
  listPaymentTypes(page = 0): Observable<PageResponse<SystemPaymentType>> {
    return this.api.getPage<SystemPaymentType>('/paymenttypes', page, 20);
  }

  createPaymentType(req: CreatePaymentTypeRequest): Observable<SystemPaymentType> {
    return this.api.post<SystemPaymentType>('/paymenttypes', req);
  }

  updatePaymentType(id: string, req: CreatePaymentTypeRequest): Observable<SystemPaymentType> {
    return this.api.put<SystemPaymentType>(`/paymenttypes/${id}`, req);
  }

  deletePaymentType(id: string): Observable<void> {
    return this.api.delete<void>(`/paymenttypes/${id}`);
  }

  // ── Funds ──────────────────────────────────────────────────────────────────
  listFunds(): Observable<Fund[]> {
    return this.api.get<Fund[]>('/funds');
  }

  createFund(req: CreateFundRequest): Observable<Fund> {
    return this.api.post<Fund>('/funds', req);
  }

  updateFund(id: string, req: CreateFundRequest): Observable<Fund> {
    return this.api.put<Fund>(`/funds/${id}`, req);
  }

  // ── Account Number Formats ─────────────────────────────────────────────────
  listAccountNumberFormats(): Observable<AccountNumberFormat[]> {
    return this.api.get<AccountNumberFormat[]>('/accountnumberformats');
  }

  createAccountNumberFormat(req: CreateAccountNumberFormatRequest): Observable<AccountNumberFormat> {
    return this.api.post<AccountNumberFormat>('/accountnumberformats', req);
  }

  updateAccountNumberFormat(id: string, req: CreateAccountNumberFormatRequest): Observable<AccountNumberFormat> {
    return this.api.put<AccountNumberFormat>(`/accountnumberformats/${id}`, req);
  }

  deleteAccountNumberFormat(id: string): Observable<void> {
    return this.api.delete<void>(`/accountnumberformats/${id}`);
  }

  // ── DataTables ─────────────────────────────────────────────────────────────
  listDataTables(): Observable<DataTable[]> {
    return this.api.get<DataTable[]>('/datatables');
  }

  createDataTable(req: CreateDataTableRequest): Observable<DataTable> {
    return this.api.post<DataTable>('/datatables', req);
  }

  deleteDataTable(name: string): Observable<void> {
    return this.api.delete<void>(`/datatables/${name}`);
  }

  // ── Surveys ────────────────────────────────────────────────────────────────
  listSurveys(): Observable<Survey[]> {
    return this.api.get<Survey[]>('/surveys');
  }

  getSurvey(id: string): Observable<Survey> {
    return this.api.get<Survey>(`/surveys/${id}`);
  }

  createSurvey(req: CreateSurveyRequest): Observable<Survey> {
    return this.api.post<Survey>('/surveys', req);
  }

  updateSurvey(id: string, req: CreateSurveyRequest): Observable<Survey> {
    return this.api.put<Survey>(`/surveys/${id}`, req);
  }

  deleteSurvey(id: string): Observable<void> {
    return this.api.delete<void>(`/surveys/${id}`);
  }

  // ── Credit Bureau ──────────────────────────────────────────────────────────
  listCreditBureaus(): Observable<CreditBureau[]> {
    return this.api.get<CreditBureau[]>('/creditbureaus');
  }

  createCreditBureau(req: CreateCreditBureauRequest): Observable<CreditBureau> {
    return this.api.post<CreditBureau>('/creditbureaus', req);
  }

  updateCreditBureau(id: string, req: CreateCreditBureauRequest): Observable<CreditBureau> {
    return this.api.put<CreditBureau>(`/creditbureaus/${id}`, req);
  }

  deleteCreditBureau(id: string): Observable<void> {
    return this.api.delete<void>(`/creditbureaus/${id}`);
  }

  activateCreditBureau(id: string): Observable<CreditBureau> {
    return this.api.post<CreditBureau>(`/creditbureaus/${id}?command=activate`, {});
  }

  deactivateCreditBureau(id: string): Observable<CreditBureau> {
    return this.api.post<CreditBureau>(`/creditbureaus/${id}?command=deactivate`, {});
  }

  listCreditBureauMappings(bureauId: string): Observable<CreditBureauMapping[]> {
    return this.api.get<CreditBureauMapping[]>(`/creditbureaus/${bureauId}/mappings`);
  }

  createCreditBureauMapping(bureauId: string, req: CreateCreditBureauMappingRequest): Observable<CreditBureauMapping> {
    return this.api.post<CreditBureauMapping>(`/creditbureaus/${bureauId}/mappings`, req);
  }

  deleteCreditBureauMapping(bureauId: string, mappingId: string): Observable<void> {
    return this.api.delete<void>(`/creditbureaus/${bureauId}/mappings/${mappingId}`);
  }

  // ── Exchange Rates ─────────────────────────────────────────────────────────
  listExchangeRates(): Observable<ExchangeRateResponse[]> {
    return this.api.get<ExchangeRateResponse[]>('/exchange-rates');
  }

  upsertExchangeRate(req: ExchangeRateRequest): Observable<ExchangeRateResponse> {
    return this.api.post<ExchangeRateResponse>('/exchange-rates', req);
  }

  deactivateExchangeRate(from: string, to: string): Observable<void> {
    return this.api.delete<void>(`/exchange-rates/${from}/${to}`);
  }

  // ── Field Configuration ────────────────────────────────────────────────────
  listFieldConfigurations(): Observable<FieldConfiguration[]> {
    return this.api.get<FieldConfiguration[]>('/fieldconfiguration');
  }

  listFieldConfigsByEntity(entityType: string): Observable<FieldConfiguration[]> {
    return this.api.get<FieldConfiguration[]>(`/fieldconfiguration/${entityType}`);
  }

  updateFieldConfig(id: string, req: UpdateFieldConfigRequest): Observable<FieldConfiguration> {
    return this.api.put<FieldConfiguration>(`/fieldconfiguration/${id}`, req);
  }

  createFieldConfig(req: CreateFieldConfigRequest): Observable<FieldConfiguration> {
    return this.api.post<FieldConfiguration>('/fieldconfiguration', req);
  }

  deleteFieldConfig(id: string): Observable<void> {
    return this.api.delete<void>(`/fieldconfiguration/${id}`);
  }
}
