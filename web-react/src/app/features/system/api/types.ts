// web-react/src/app/features/system/api/types.ts

// ── Codes ──────────────────────────────────────────────────────────────────────
export interface CodeValue {
  id: string
  name: string
  description?: string
  position: number
  active: boolean
}

export interface Code {
  id: string
  name: string
  systemDefined: boolean
  codeValues?: CodeValue[]
}

export interface CreateCodeRequest {
  name: string
}

export interface CreateCodeValueRequest {
  name: string
  description?: string
  position?: number
}

export interface UpdateCodeValueRequest {
  name: string
  description?: string
  position?: number
  active?: boolean
}

// ── Global Configuration ───────────────────────────────────────────────────────
export interface GlobalConfiguration {
  id: string
  name: string
  enabled: boolean
  value?: number
  stringValue?: string
  numericValue?: number
  booleanValue?: boolean
  description?: string
}

export interface UpdateGlobalConfigRequest {
  enabled: boolean
  value?: number
  stringValue?: string
}

// ── Floating Rates ─────────────────────────────────────────────────────────────
export interface FloatingRatePeriod {
  id?: string
  fromDate: string
  interestRate: number
  isDifferentialToBaseLendingRate: boolean
}

export interface FloatingRate {
  id: string
  name: string
  isBaseLendingRate: boolean
  isActive: boolean
  ratePeriods: FloatingRatePeriod[]
  createdOn?: string
}

export interface CreateFloatingRateRequest {
  name: string
  isBaseLendingRate: boolean
  isActive: boolean
  ratePeriods: FloatingRatePeriod[]
}

// ── Taxes ──────────────────────────────────────────────────────────────────────
export interface TaxComponent {
  id: string
  name: string
  percentage: number
  startDate: string
  creditAccountId?: string
  debitAccountId?: string
}

export interface CreateTaxComponentRequest {
  name: string
  percentage: number
  startDate: string
  creditAccountId?: string
  debitAccountId?: string
}

export interface TaxGroupMapping {
  id?: string
  taxComponentId: string
  taxComponentName?: string
  startDate: string
  endDate?: string
}

export interface TaxGroup {
  id: string
  name: string
  taxGroupMappings: TaxGroupMapping[]
}

export interface CreateTaxGroupRequest {
  name: string
  taxGroupMappings: TaxGroupMapping[]
}

// ── Account Number Algorithms ──────────────────────────────────────────────────
export type AlgorithmType = 'MIFOS' | 'NUBAN'
export type ValidationMode = 'STRICT' | 'PARANOID'

export interface TenantAlgorithmConfig {
  tenantId: string
  bankCode?: string
  validationMode: ValidationMode
  algorithms: Record<string, AlgorithmType>
}

export interface UpdateAlgorithmRequest {
  bankCode?: string
  validationMode: ValidationMode
  algorithms: Record<string, AlgorithmType>
}
