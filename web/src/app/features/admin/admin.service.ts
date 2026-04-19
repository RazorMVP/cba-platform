import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from '../../core/api/api.service';
import { PageResponse } from '../../core/models/api-response.model';

// ── Audit Log ─────────────────────────────────────────────────────────────────
export interface AuditLog {
  id: string;
  tenantId?: string;
  entityType: string;
  entityId: string;
  action: string;
  changedBy: string;
  changedAt: string;
  oldValues?: string;
  newValues?: string;
  sessionId?: string;
}

export interface AuditFilter {
  entityType?: string;
  entityId?: string;
  changedBy?: string;
  from?: string;
  to?: string;
}

// ── Users ─────────────────────────────────────────────────────────────────────
export interface PlatformUser {
  id: string;
  username: string;
  firstname: string;
  lastname: string;
  email: string;
  officeId: string;
  officeName: string;
  roles: UserRole[];
  enabled: boolean;
  createdAt: string;
}

export interface UserRole {
  id: string;
  name: string;
}

export interface CreateUserRequest {
  username: string;
  firstname: string;
  lastname: string;
  email: string;
  password: string;
  officeId: string;
  roleIds: string[];
}

// ── Roles ─────────────────────────────────────────────────────────────────────
export interface Role {
  id: string;
  name: string;
  description: string;
  disabled: boolean;
  permissions: Permission[];
}

export interface Permission {
  id: string;
  grouping: string;
  code: string;
  entityName: string;
  actionName: string;
  canMakerChecker: boolean;
}

export interface CreateRoleRequest {
  name: string;
  description: string;
}

export interface UpdatePermissionsRequest {
  permissionIds: string[];
}

// ── Offices ───────────────────────────────────────────────────────────────────
export interface Office {
  id: string;
  name: string;
  externalId: string;
  openingDate: string;
  parentId: string | null;
  parentName: string | null;
  hierarchy: string;
}

export interface CreateOfficeRequest {
  name: string;
  externalId: string;
  openingDate: string;
  parentId?: string;
}

// ── Hooks ─────────────────────────────────────────────────────────────────────
export type HookType = 'WEB' | 'SMS';

export interface Hook {
  id: string;
  name: string;
  hookType: HookType;
  url: string;
  events: string[];
  enabled: boolean;
  createdAt: string;
}

export interface CreateHookRequest {
  name: string;
  hookType: HookType;
  url: string;
  events: string[];
}

// ── Maker-Checker ─────────────────────────────────────────────────────────────
export type MakerCheckerStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface MakerCheckerEntry {
  id: string;
  entityType: string;
  actionName: string;
  madeByUsername: string;
  madeOnDate: string;
  checkedByUsername: string | null;
  checkedOnDate: string | null;
  status: MakerCheckerStatus;
  resourceId: string | null;
}

// ── Tenants ───────────────────────────────────────────────────────────────────
export interface Tenant {
  id: string;
  code: string;
  name: string;
  currencyCode: string;
  countryCode: string | null;
  localeCode: string;
}

// ── Notifications ─────────────────────────────────────────────────────────────

export type NotificationDeliveryMethod = 'EMAIL' | 'SMS';
export type NotificationLogStatus      = 'SENT' | 'FAILED' | 'SKIPPED';

export interface NotificationTemplate {
  id: string;
  name: string;
  eventType: string;
  deliveryMethod: NotificationDeliveryMethod;
  subject?: string;
  body: string;
  active: boolean;
}

export interface NotificationLog {
  id: string;
  templateId: string;
  eventType: string;
  recipientRef?: string;
  deliveryMethod: NotificationDeliveryMethod;
  status: NotificationLogStatus;
  sentAt: string;
}

export interface CreateTemplateRequest {
  name: string;
  eventType: string;
  deliveryMethod: NotificationDeliveryMethod;
  subject?: string;
  body: string;
}

// ── SMS Campaigns ─────────────────────────────────────────────────────────

export type CampaignType  = 'INDIVIDUAL' | 'ALL' | 'QUERY';
export type TriggerType   = 'DIRECT' | 'SCHEDULED' | 'TRIGGERED';
export type CampaignStatus = 'PENDING' | 'WAITING_FOR_ACTIVATION' | 'ACTIVE' | 'CLOSED' | 'DELETED';
export type SmsDeliveryStatus = 'PENDING' | 'SENT' | 'FAILED' | 'INVALID';

export interface SmsCampaign {
  id: string;
  campaignName: string;
  campaignType: CampaignType;
  triggerType: TriggerType;
  message: string;
  status: CampaignStatus;
  recurrence?: string;
  runDate?: string;
  nextTriggerDate?: string;
  lastTriggerDate?: string;
  submittedOnDate?: string;
}

export interface SmsMessage {
  id: string;
  campaignId: string;
  mobileNumber?: string;
  recipientRef?: string;
  deliveryStatus: SmsDeliveryStatus;
  submittedOnDate?: string;
}

export interface CreateSmsCampaignRequest {
  campaignName: string;
  campaignType: CampaignType;
  triggerType: TriggerType;
  message: string;
  recurrence?: string;
  runDate?: string;
}

// ── Staff ─────────────────────────────────────────────────────────────────────
export interface Staff {
  id: string;
  firstName: string;
  lastName: string;
  displayName: string;
  email: string | null;
  mobileNo: string | null;
  joiningDate: string | null;
  loanOfficer: boolean;
  active: boolean;
  officeId: string;
  officeName: string | null;
}

export interface CreateStaffRequest {
  firstName: string;
  lastName: string;
  email: string;
  mobileNo: string;
  joiningDate: string;
  loanOfficer: boolean;
  officeId: string;
}

// ── Standing Instructions ─────────────────────────────────────────────────────
export type InstructionType = 'FIXED' | 'OUTSTANDING_BALANCE';
export type InstructionPriority = 'HIGH' | 'MEDIUM' | 'LOW' | 'URGENT';
export type InstructionStatus = 'ACTIVE' | 'DISABLED' | 'DELETED';
export type RecurrenceType = 'PERIODIC_RECURRENCE' | 'AS_PER_DUES';

export interface StandingInstruction {
  id: string;
  name: string;
  fromAccountId: string;
  toAccountId: string;
  instructionType: InstructionType;
  priority: InstructionPriority;
  status: InstructionStatus;
  recurrenceType: RecurrenceType;
  recurrenceFrequency: number | null;
  amount: number | null;
  validFrom: string | null;
  validTill: string | null;
  createdAt: string;
}

export interface CreateStandingInstructionRequest {
  name: string;
  fromAccountId: string;
  toAccountId: string;
  instructionType: InstructionType;
  priority: InstructionPriority;
  recurrenceType: RecurrenceType;
  recurrenceFrequency: number;
  amount: number;
  validFrom: string;
  validTill: string;
}

// ── TPP (Open Banking admin) ──────────────────────────────────────────────────
export type TppStatus = 'ACTIVE' | 'REVOKED' | 'PENDING';

export interface TppRegistration {
  id: string;
  name: string;
  clientId: string;
  country: string;
  allowedScopes: string[];
  certificateExpiry: string | null;
  status: TppStatus;
  registeredAt: string;
}

export interface RegisterTppRequest {
  name: string;
  clientId: string;
  country: string;
  allowedScopes: string[];
  certificateExpiry?: string;
}

// ── Login History ─────────────────────────────────────────────────────────────
export type LoginStatus = 'SUCCESS' | 'FAILURE' | 'LOCKED' | 'LOGOUT';

export interface LoginHistoryEvent {
  id: string;
  userId: string;
  username: string;
  ipAddress: string;
  userAgent: string;
  status: LoginStatus;
  failureReason?: string;
  sessionRef?: string;
  createdAt: string;
}

export interface LoginHistoryFilter {
  status?: string;
  username?: string;
  from?: string;
  to?: string;
  page?: number;
}

export interface LoginEventSummary {
  periodDays: number;
  successLogins: number;
  failedLogins: number;
  lockedAccounts: number;
  uniqueUsers: number;
  topFailedUsers: { username: string; failureCount: number }[];
}

// ── Compliance Reports ────────────────────────────────────────────────────────
export interface ComplianceRow {
  action: string;
  entity_type: string;
  event_count: number;
  unique_actors: number;
}

export interface FailedLoginRow {
  username: string;
  ip_address: string;
  status: string;
  attempt_count: number;
  last_attempt: string;
}

export interface UserActivityRow {
  user_id: string;
  total_actions: number;
  entity_types_touched: number;
  last_action: string;
}

export interface DataAccessRow {
  entity_id: string;
  action: string;
  changed_by: string;
  changed_at: string;
}

export interface BulkImportRowError { row: number; field: string; message: string; }
export interface BulkImportResult {
  jobId: string; entityType: string;
  totalRows: number; successCount: number; failureCount: number;
  status: 'COMPLETED' | 'PARTIAL' | 'FAILED';
  errors: BulkImportRowError[];
}
export interface BulkImportJob {
  id: string; entityType: string; fileName: string;
  totalRows: number; successCount: number; failureCount: number;
  status: string; errorSummary?: string; importedBy?: string; createdAt: string;
}

export interface SecurityPolicy {
  bruteForceProtected: boolean;
  maxLoginFailures: number;
  lockoutDurationSeconds: number;
  failureResetWindowSeconds: number;
  minPasswordLength: number;
  requireUppercase: boolean;
  requireLowercase: boolean;
  requireDigits: boolean;
  requireSpecialChars: boolean;
  passwordHistoryCount: number;
  ssoSessionIdleTimeoutSeconds: number;
  ssoSessionMaxLifespanSeconds: number;
  accessTokenLifespanSeconds: number;
  rawPasswordPolicy: string;
}

// ── Fraud & Risk ──────────────────────────────────────────────────────────────
export interface FraudRule {
  id: string;
  name: string;
  ruleType: string;
  enabled: boolean;
  blocking: boolean;
  severity: string;
  params: string;
  description?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface FraudAlert {
  id: string;
  ruleId?: string;
  ruleName?: string;
  customerId?: string;
  accountId?: string;
  transactionId?: string;
  severity: string;
  status: string;
  alertType: string;
  details?: string;
  caseId?: string;
  reviewedBy?: string;
  resolvedAt?: string;
  createdAt: string;
  updatedAt?: string;
}

export interface FraudCase {
  id: string;
  caseNumber: string;
  title: string;
  customerId?: string;
  status: string;
  riskLevel: string;
  assignedTo?: string;
  resolutionNotes?: string;
  resolvedAt?: string;
  createdAt: string;
  updatedAt?: string;
}

export interface BlacklistEntry {
  id: string;
  entityType: string;
  entityValue: string;
  reason?: string;
  source: string;
  active: boolean;
  addedBy?: string;
  expiresAt?: string;
  createdAt: string;
  updatedAt?: string;
}

export interface CustomerRiskScore {
  id?: string;
  customerId: string;
  score: number;
  riskLevel: string;
  factors?: string;
  openAlertsCount: number;
  confirmedCasesCount: number;
  blacklistHits: number;
  calculatedAt?: string;
}

@Injectable({ providedIn: 'root' })
export class AdminService {
  private readonly api = inject(ApiService);

  // ── Users ──────────────────────────────────────────────────────────────────
  listUsers(): Observable<PlatformUser[]> {
    return this.api.get<PlatformUser[]>('/users');
  }

  createUser(req: CreateUserRequest): Observable<PlatformUser> {
    return this.api.post<PlatformUser>('/users', req);
  }

  enableUser(id: string): Observable<PlatformUser> {
    return this.api.command<PlatformUser>(`/users/${id}`, 'enable');
  }

  disableUser(id: string): Observable<PlatformUser> {
    return this.api.command<PlatformUser>(`/users/${id}`, 'disable');
  }

  deleteUser(id: string): Observable<void> {
    return this.api.delete<void>(`/users/${id}`);
  }

  // ── Roles ──────────────────────────────────────────────────────────────────
  listRoles(): Observable<Role[]> {
    return this.api.get<Role[]>('/roles');
  }

  createRole(req: CreateRoleRequest): Observable<Role> {
    return this.api.post<Role>('/roles', req);
  }

  updateRole(id: string, req: CreateRoleRequest): Observable<Role> {
    return this.api.put<Role>(`/roles/${id}`, req);
  }

  listPermissions(): Observable<Permission[]> {
    return this.api.get<Permission[]>('/roles/permissions');
  }

  updateRolePermissions(roleId: string, req: UpdatePermissionsRequest): Observable<Role> {
    return this.api.put<Role>(`/roles/${roleId}/permissions`, req);
  }

  deleteRole(id: string): Observable<void> {
    return this.api.delete<void>(`/roles/${id}`);
  }

  // ── Offices ────────────────────────────────────────────────────────────────
  listOffices(): Observable<Office[]> {
    return this.api.get<Office[]>('/offices');
  }

  createOffice(req: CreateOfficeRequest): Observable<Office> {
    return this.api.post<Office>('/offices', req);
  }

  updateOffice(id: string, req: CreateOfficeRequest): Observable<Office> {
    return this.api.put<Office>(`/offices/${id}`, req);
  }

  // ── Hooks ──────────────────────────────────────────────────────────────────
  listHooks(): Observable<Hook[]> {
    return this.api.get<Hook[]>('/hooks');
  }

  createHook(req: CreateHookRequest): Observable<Hook> {
    return this.api.post<Hook>('/hooks', req);
  }

  updateHook(id: string, req: CreateHookRequest): Observable<Hook> {
    return this.api.put<Hook>(`/hooks/${id}`, req);
  }

  deleteHook(id: string): Observable<void> {
    return this.api.delete<void>(`/hooks/${id}`);
  }

  // ── Maker-Checker ──────────────────────────────────────────────────────────
  listMakerChecker(status?: MakerCheckerStatus): Observable<MakerCheckerEntry[]> {
    const params = status ? { status } : undefined;
    return this.api.get<MakerCheckerEntry[]>('/makercheckers', params);
  }

  approveMakerChecker(id: string): Observable<MakerCheckerEntry> {
    return this.api.command<MakerCheckerEntry>(`/makercheckers/${id}`, 'approve');
  }

  rejectMakerChecker(id: string): Observable<MakerCheckerEntry> {
    return this.api.command<MakerCheckerEntry>(`/makercheckers/${id}`, 'reject');
  }

  // ── Tenants ────────────────────────────────────────────────────────────────
  listTenants(): Observable<Tenant[]> {
    return this.api.get<Tenant[]>('/tenants');
  }

  // ── Notifications ──────────────────────────────────────────────────────────
  listNotificationTemplates(activeOnly = false): Observable<NotificationTemplate[]> {
    const params = activeOnly ? { active: 'true' } : undefined;
    return this.api.get<NotificationTemplate[]>('/notifications/templates', params);
  }

  createNotificationTemplate(req: CreateTemplateRequest): Observable<NotificationTemplate> {
    return this.api.post<NotificationTemplate>('/notifications/templates', req);
  }

  updateNotificationTemplate(id: string, req: CreateTemplateRequest): Observable<NotificationTemplate> {
    return this.api.put<NotificationTemplate>(`/notifications/templates/${id}`, req);
  }

  deactivateNotificationTemplate(id: string): Observable<void> {
    return this.api.delete<void>(`/notifications/templates/${id}`);
  }

  sendTestNotification(templateId: string, recipientRef: string): Observable<NotificationLog> {
    return this.api.post<NotificationLog>('/notifications/test', { templateId, recipientRef });
  }

  listNotificationHistory(params?: Record<string, string>): Observable<NotificationLog[]> {
    return this.api.get<NotificationLog[]>('/notifications/history', params);
  }

  // ── TPP ────────────────────────────────────────────────────────────────────
  listTpps(): Observable<TppRegistration[]> {
    return this.api.get<TppRegistration[]>('/openbanking/tpp');
  }

  registerTpp(req: RegisterTppRequest): Observable<TppRegistration> {
    return this.api.post<TppRegistration>('/openbanking/tpp', req);
  }

  activateTpp(id: string): Observable<TppRegistration> {
    return this.api.command<TppRegistration>(`/openbanking/tpp/${id}`, 'activate');
  }

  revokeTpp(id: string): Observable<TppRegistration> {
    return this.api.command<TppRegistration>(`/openbanking/tpp/${id}`, 'revoke');
  }

  // ── Audit Log ──────────────────────────────────────────────────────────────
  listAuditLogs(page: number, filters: AuditFilter): Observable<PageResponse<AuditLog>> {
    const params: Record<string, string> = { sort: 'changedAt,desc' };
    if (filters.entityType) params['entityType'] = filters.entityType;
    if (filters.entityId)   params['entityId']   = filters.entityId;
    if (filters.changedBy)  params['changedBy']  = filters.changedBy;
    if (filters.from)       params['from']       = filters.from;
    if (filters.to)         params['to']         = filters.to;
    const hasFilter = !!(filters.entityType || filters.changedBy || filters.from || filters.to);
    return this.api.getPage<AuditLog>(hasFilter ? '/audits/search' : '/audits', page, 20, params);
  }

  getAuditLog(id: string): Observable<AuditLog> {
    return this.api.get<AuditLog>(`/audits/${id}`);
  }

  // ── SMS Campaigns ──────────────────────────────────────────────────────────
  listSmsCampaigns(page = 0): Observable<PageResponse<SmsCampaign>> {
    return this.api.getPage<SmsCampaign>('/smscampaigns', page, 20);
  }

  createSmsCampaign(req: CreateSmsCampaignRequest): Observable<SmsCampaign> {
    return this.api.post<SmsCampaign>('/smscampaigns', req);
  }

  updateSmsCampaign(id: string, req: CreateSmsCampaignRequest): Observable<SmsCampaign> {
    return this.api.put<SmsCampaign>(`/smscampaigns/${id}`, req);
  }

  deleteSmsCampaign(id: string): Observable<void> {
    return this.api.delete<void>(`/smscampaigns/${id}`);
  }

  activateSmsCampaign(id: string): Observable<SmsCampaign> {
    return this.api.command<SmsCampaign>(`/smscampaigns/${id}`, 'activate');
  }

  listSmsMessages(campaignId: string): Observable<SmsMessage[]> {
    return this.api.get<SmsMessage[]>(`/smscampaigns/${campaignId}/messages`);
  }

  // ── Staff ──────────────────────────────────────────────────────────────────
  listStaff(officeId?: string): Observable<Staff[]> {
    const params = officeId ? { officeId } : undefined;
    return this.api.get<Staff[]>('/staff', params);
  }

  getStaff(id: string): Observable<Staff> {
    return this.api.get<Staff>(`/staff/${id}`);
  }

  createStaff(req: CreateStaffRequest): Observable<Staff> {
    return this.api.post<Staff>('/staff', req);
  }

  updateStaff(id: string, req: CreateStaffRequest): Observable<Staff> {
    return this.api.put<Staff>(`/staff/${id}`, req);
  }

  // ── Standing Instructions ──────────────────────────────────────────────────
  listStandingInstructions(page = 0): Observable<PageResponse<StandingInstruction>> {
    return this.api.getPage<StandingInstruction>('/standinginstructions', page, 20);
  }

  createStandingInstruction(req: CreateStandingInstructionRequest): Observable<StandingInstruction> {
    return this.api.post<StandingInstruction>('/standinginstructions', req);
  }

  updateStandingInstruction(id: string, req: CreateStandingInstructionRequest): Observable<StandingInstruction> {
    return this.api.put<StandingInstruction>(`/standinginstructions/${id}`, req);
  }

  deleteStandingInstruction(id: string): Observable<void> {
    return this.api.delete<void>(`/standinginstructions/${id}`);
  }

  disableStandingInstruction(id: string): Observable<StandingInstruction> {
    return this.api.command<StandingInstruction>(`/standinginstructions/${id}`, 'disable');
  }

  enableStandingInstruction(id: string): Observable<StandingInstruction> {
    return this.api.command<StandingInstruction>(`/standinginstructions/${id}`, 'enable');
  }

  // ── Login History ──────────────────────────────────────────────────────────
  recordLoginEvent(status: string, sessionRef?: string): Observable<LoginHistoryEvent> {
    return this.api.post<LoginHistoryEvent>('/auth/events', { status, sessionRef });
  }

  listLoginEvents(params: LoginHistoryFilter): Observable<PageResponse<LoginHistoryEvent>> {
    const p: Record<string, string> = {
      page: String(params.page ?? 0),
      size: '20',
    };
    if (params.status)   p['status']   = params.status;
    if (params.username) p['username'] = params.username;
    if (params.from)     p['from']     = params.from;
    if (params.to)       p['to']       = params.to;
    return this.api.getPage<LoginHistoryEvent>('/auth/events', params.page ?? 0, 20, p);
  }

  loginEventSummary(days = 30): Observable<LoginEventSummary> {
    return this.api.get<LoginEventSummary>('/auth/events/summary', { days });
  }

  // ── Compliance Reports ─────────────────────────────────────────────────────
  complianceAuditSummary(days = 30): Observable<ComplianceRow[]> {
    return this.api.get<ComplianceRow[]>('/compliance/reports/audit-summary', { days });
  }

  complianceFailedLogins(days = 30): Observable<FailedLoginRow[]> {
    return this.api.get<FailedLoginRow[]>('/compliance/reports/failed-logins', { days });
  }

  complianceUserActivity(days = 30): Observable<UserActivityRow[]> {
    return this.api.get<UserActivityRow[]>('/compliance/reports/user-activity', { days });
  }

  complianceDataAccess(days = 30, entityType = 'LOAN'): Observable<DataAccessRow[]> {
    return this.api.get<DataAccessRow[]>('/compliance/reports/data-access', { days, entityType });
  }

  // ── Bulk Import ───────────────────────────────────────────────────────────
  importCustomers(file: File): Observable<BulkImportResult> {
    const fd = new FormData();
    fd.append('file', file);
    return this.api.postForm<BulkImportResult>('/bulkimport/customers', fd);
  }

  importLoans(file: File): Observable<BulkImportResult> {
    const fd = new FormData();
    fd.append('file', file);
    return this.api.postForm<BulkImportResult>('/bulkimport/loans', fd);
  }

  bulkImportJobs(entityType?: string): Observable<BulkImportJob[]> {
    const p: Record<string, string> = {};
    if (entityType) p['entityType'] = entityType;
    return this.api.get<BulkImportJob[]>('/bulkimport/jobs', p);
  }

  // ── Security Policy ───────────────────────────────────────────────────────
  getSecurityPolicy(): Observable<SecurityPolicy> {
    return this.api.get<SecurityPolicy>('/security-policy');
  }

  updateSecurityPolicy(req: Partial<SecurityPolicy>): Observable<SecurityPolicy> {
    return this.api.put<SecurityPolicy>('/security-policy', req);
  }

  // ── Fraud & Risk ──────────────────────────────────────────────────────────
  listFraudRules(page = 0, size = 50): Observable<PageResponse<FraudRule>> {
    return this.api.getPage<FraudRule>('/fraud/rules', page, size);
  }

  updateFraudRule(id: string, req: Partial<FraudRule>): Observable<FraudRule> {
    return this.api.put<FraudRule>(`/fraud/rules/${id}`, req);
  }

  listFraudAlerts(status?: string, severity?: string, page = 0, size = 20): Observable<PageResponse<FraudAlert>> {
    const p: Record<string, string> = {};
    if (status) p['status'] = status;
    if (severity) p['severity'] = severity;
    return this.api.getPage<FraudAlert>('/fraud/alerts', page, size, p);
  }

  reviewFraudAlert(id: string, reviewedBy: string): Observable<FraudAlert> {
    return this.api.post<FraudAlert>(`/fraud/alerts/${id}/review`, { reviewedBy });
  }

  closeFraudAlert(id: string, status: string, reviewedBy: string): Observable<FraudAlert> {
    return this.api.post<FraudAlert>(`/fraud/alerts/${id}/close`, { status, reviewedBy });
  }

  linkAlertToCase(alertId: string, caseId: string): Observable<FraudCase> {
    return this.api.post<FraudCase>(`/fraud/cases/${caseId}/alerts/${alertId}`, {});
  }

  listFraudCases(status?: string, riskLevel?: string, page = 0, size = 20): Observable<PageResponse<FraudCase>> {
    const p: Record<string, string> = {};
    if (status) p['status'] = status;
    if (riskLevel) p['riskLevel'] = riskLevel;
    return this.api.getPage<FraudCase>('/fraud/cases', page, size, p);
  }

  createFraudCase(title: string, customerId?: string, riskLevel = 'MEDIUM', assignedTo = ''): Observable<FraudCase> {
    return this.api.post<FraudCase>('/fraud/cases', { title, customerId, riskLevel, assignedTo });
  }

  updateFraudCase(id: string, status: string, assignedTo: string, resolutionNotes: string): Observable<FraudCase> {
    return this.api.put<FraudCase>(`/fraud/cases/${id}`, { status, assignedTo, resolutionNotes });
  }

  listBlacklist(entityType?: string, active?: boolean, page = 0, size = 20): Observable<PageResponse<BlacklistEntry>> {
    const p: Record<string, string> = {};
    if (entityType) p['entityType'] = entityType;
    if (active !== undefined) p['active'] = String(active);
    return this.api.getPage<BlacklistEntry>('/fraud/blacklist', page, size, p);
  }

  searchBlacklist(q: string): Observable<BlacklistEntry[]> {
    return this.api.get<BlacklistEntry[]>('/fraud/blacklist/search', { q });
  }

  addBlacklistEntry(req: {
    entityType: string; entityValue: string; reason: string;
    source: string; expiresAt: string; addedBy: string;
  }): Observable<BlacklistEntry> {
    return this.api.post<BlacklistEntry>('/fraud/blacklist', req);
  }

  updateBlacklistEntry(id: string, reason: string, expiresAt?: string): Observable<BlacklistEntry> {
    return this.api.put<BlacklistEntry>(`/fraud/blacklist/${id}`, { reason, expiresAt });
  }

  deactivateBlacklistEntry(id: string): Observable<BlacklistEntry> {
    return this.api.delete<BlacklistEntry>(`/fraud/blacklist/${id}`);
  }

  listRiskScores(riskLevel?: string, page = 0, size = 20): Observable<PageResponse<CustomerRiskScore>> {
    const p: Record<string, string> = {};
    if (riskLevel) p['riskLevel'] = riskLevel;
    return this.api.getPage<CustomerRiskScore>('/fraud/risk-scores', page, size, p);
  }

  getRiskScore(customerId: string): Observable<CustomerRiskScore> {
    return this.api.get<CustomerRiskScore>(`/fraud/risk-scores/${customerId}`);
  }

  recalculateRiskScore(customerId: string): Observable<unknown> {
    return this.api.post(`/fraud/risk-scores/${customerId}/recalculate`, {});
  }
}
