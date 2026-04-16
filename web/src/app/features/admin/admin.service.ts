import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from '../../core/api/api.service';

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

@Injectable({ providedIn: 'root' })
export class AdminService {
  private readonly api = inject(ApiService);

  // ── Users ──────────────────────────────────────────────────────────────────
  listUsers(): Observable<PlatformUser[]> {
    return this.api.get<PlatformUser[]>('/api/v1/users');
  }

  createUser(req: CreateUserRequest): Observable<PlatformUser> {
    return this.api.post<PlatformUser>('/api/v1/users', req);
  }

  enableUser(id: string): Observable<PlatformUser> {
    return this.api.command<PlatformUser>(`/api/v1/users/${id}`, 'enable');
  }

  disableUser(id: string): Observable<PlatformUser> {
    return this.api.command<PlatformUser>(`/api/v1/users/${id}`, 'disable');
  }

  deleteUser(id: string): Observable<void> {
    return this.api.delete<void>(`/api/v1/users/${id}`);
  }

  // ── Roles ──────────────────────────────────────────────────────────────────
  listRoles(): Observable<Role[]> {
    return this.api.get<Role[]>('/api/v1/roles');
  }

  createRole(req: CreateRoleRequest): Observable<Role> {
    return this.api.post<Role>('/api/v1/roles', req);
  }

  updateRole(id: string, req: CreateRoleRequest): Observable<Role> {
    return this.api.put<Role>(`/api/v1/roles/${id}`, req);
  }

  listPermissions(): Observable<Permission[]> {
    return this.api.get<Permission[]>('/api/v1/roles/permissions');
  }

  updateRolePermissions(roleId: string, req: UpdatePermissionsRequest): Observable<Role> {
    return this.api.put<Role>(`/api/v1/roles/${roleId}/permissions`, req);
  }

  deleteRole(id: string): Observable<void> {
    return this.api.delete<void>(`/api/v1/roles/${id}`);
  }

  // ── Offices ────────────────────────────────────────────────────────────────
  listOffices(): Observable<Office[]> {
    return this.api.get<Office[]>('/api/v1/offices');
  }

  createOffice(req: CreateOfficeRequest): Observable<Office> {
    return this.api.post<Office>('/api/v1/offices', req);
  }

  updateOffice(id: string, req: CreateOfficeRequest): Observable<Office> {
    return this.api.put<Office>(`/api/v1/offices/${id}`, req);
  }

  // ── Hooks ──────────────────────────────────────────────────────────────────
  listHooks(): Observable<Hook[]> {
    return this.api.get<Hook[]>('/api/v1/hooks');
  }

  createHook(req: CreateHookRequest): Observable<Hook> {
    return this.api.post<Hook>('/api/v1/hooks', req);
  }

  updateHook(id: string, req: CreateHookRequest): Observable<Hook> {
    return this.api.put<Hook>(`/api/v1/hooks/${id}`, req);
  }

  deleteHook(id: string): Observable<void> {
    return this.api.delete<void>(`/api/v1/hooks/${id}`);
  }

  // ── Maker-Checker ──────────────────────────────────────────────────────────
  listMakerChecker(status?: MakerCheckerStatus): Observable<MakerCheckerEntry[]> {
    const params = status ? { status } : undefined;
    return this.api.get<MakerCheckerEntry[]>('/api/v1/makercheckers', params);
  }

  approveMakerChecker(id: string): Observable<MakerCheckerEntry> {
    return this.api.command<MakerCheckerEntry>(`/api/v1/makercheckers/${id}`, 'approve');
  }

  rejectMakerChecker(id: string): Observable<MakerCheckerEntry> {
    return this.api.command<MakerCheckerEntry>(`/api/v1/makercheckers/${id}`, 'reject');
  }

  // ── Tenants ────────────────────────────────────────────────────────────────
  listTenants(): Observable<Tenant[]> {
    return this.api.get<Tenant[]>('/api/v1/tenants');
  }

  // ── Notifications ──────────────────────────────────────────────────────────
  listNotificationTemplates(activeOnly = false): Observable<NotificationTemplate[]> {
    const params = activeOnly ? { active: 'true' } : undefined;
    return this.api.get<NotificationTemplate[]>('/api/v1/notifications/templates', params);
  }

  createNotificationTemplate(req: CreateTemplateRequest): Observable<NotificationTemplate> {
    return this.api.post<NotificationTemplate>('/api/v1/notifications/templates', req);
  }

  updateNotificationTemplate(id: string, req: CreateTemplateRequest): Observable<NotificationTemplate> {
    return this.api.put<NotificationTemplate>(`/api/v1/notifications/templates/${id}`, req);
  }

  deactivateNotificationTemplate(id: string): Observable<void> {
    return this.api.delete<void>(`/api/v1/notifications/templates/${id}`);
  }

  sendTestNotification(templateId: string, recipientRef: string): Observable<NotificationLog> {
    return this.api.post<NotificationLog>('/api/v1/notifications/test', { templateId, recipientRef });
  }

  listNotificationHistory(params?: Record<string, string>): Observable<NotificationLog[]> {
    return this.api.get<NotificationLog[]>('/api/v1/notifications/history', params);
  }

  // ── TPP ────────────────────────────────────────────────────────────────────
  listTpps(): Observable<TppRegistration[]> {
    return this.api.get<TppRegistration[]>('/api/v1/openbanking/tpp');
  }

  registerTpp(req: RegisterTppRequest): Observable<TppRegistration> {
    return this.api.post<TppRegistration>('/api/v1/openbanking/tpp', req);
  }

  activateTpp(id: string): Observable<TppRegistration> {
    return this.api.command<TppRegistration>(`/api/v1/openbanking/tpp/${id}`, 'activate');
  }

  revokeTpp(id: string): Observable<TppRegistration> {
    return this.api.command<TppRegistration>(`/api/v1/openbanking/tpp/${id}`, 'revoke');
  }
}
