// web-react/src/app/features/admin/api/types.ts

// ── Users ──────────────────────────────────────────────────────────────────────
export interface UserRole {
  id: string
  name: string
}

export interface PlatformUser {
  id: string
  username: string
  firstname: string
  lastname: string
  email: string
  officeId: string
  officeName: string
  roles: UserRole[]
  enabled: boolean
  createdAt: string
}

export interface CreateUserRequest {
  username: string
  firstname: string
  lastname: string
  email: string
  password: string
  officeId: string
  roleIds: string[]
}

// ── Roles ──────────────────────────────────────────────────────────────────────
export interface Permission {
  id: string
  grouping: string
  code: string
  entityName: string
  actionName: string
  canMakerChecker: boolean
}

export interface Role {
  id: string
  name: string
  description: string
  disabled: boolean
  permissions: Permission[]
}

export interface CreateRoleRequest {
  name: string
  description: string
}

export interface UpdatePermissionsRequest {
  permissionIds: string[]
}

// ── Offices ────────────────────────────────────────────────────────────────────
export interface Office {
  id: string
  name: string
  externalId: string
  openingDate: string
  parentId: string | null
  parentName: string | null
  hierarchy: string
}

export interface CreateOfficeRequest {
  name: string
  externalId: string
  openingDate: string
  parentId?: string
}

// ── Hooks ──────────────────────────────────────────────────────────────────────
export type HookType = 'WEB' | 'SMS'

export interface Hook {
  id: string
  name: string
  hookType: HookType
  url: string
  events: string[]
  enabled: boolean
  createdAt: string
}

export interface CreateHookRequest {
  name: string
  hookType: HookType
  url: string
  events: string[]
}

// ── Maker-Checker ──────────────────────────────────────────────────────────────
export type MakerCheckerStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

export interface MakerCheckerEntry {
  id: string
  entityType: string
  actionName: string
  madeByUsername: string
  madeOnDate: string
  checkedByUsername: string | null
  checkedOnDate: string | null
  status: MakerCheckerStatus
  resourceId: string | null
}

// ── Notifications ──────────────────────────────────────────────────────────────
export type NotificationDeliveryMethod = 'EMAIL' | 'SMS'
export type NotificationLogStatus = 'SENT' | 'FAILED' | 'SKIPPED'

export interface NotificationTemplate {
  id: string
  name: string
  eventType: string
  deliveryMethod: NotificationDeliveryMethod
  subject?: string
  body: string
  active: boolean
}

export interface NotificationLog {
  id: string
  templateId: string
  eventType: string
  recipientRef?: string
  deliveryMethod: NotificationDeliveryMethod
  status: NotificationLogStatus
  sentAt: string
}

export interface CreateTemplateRequest {
  name: string
  eventType: string
  deliveryMethod: NotificationDeliveryMethod
  subject?: string
  body: string
}

// ── TPP (Open Banking admin) ───────────────────────────────────────────────────
export type TppStatus = 'ACTIVE' | 'REVOKED' | 'PENDING'

export interface TppRegistration {
  id: string
  name: string
  clientId: string
  country: string
  allowedScopes: string[]
  certificateExpiry: string | null
  status: TppStatus
  registeredAt: string
}

export interface RegisterTppRequest {
  name: string
  clientId: string
  country: string
  allowedScopes: string[]
  certificateExpiry?: string
}
