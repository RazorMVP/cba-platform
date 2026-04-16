// web-react/src/app/features/groups/api/types.ts

// ── Groups ─────────────────────────────────────────────────────────────────────
export type GroupStatus = 'PENDING' | 'ACTIVE' | 'CLOSED'

export interface Group {
  id: string
  name: string
  externalId?: string
  status: GroupStatus
  officeId: string
  officeName: string
  staffId?: string
  staffName?: string
  activationDate?: string
  submittedOnDate: string
}

export interface CreateGroupRequest {
  name: string
  officeId: string
  externalId?: string
  staffId?: string
}

export interface GroupMember {
  id: string
  displayName: string
  accountNo: string
}

export interface GlimAccount {
  id: string
  accountNo: string
  clientId: string
  clientName: string
  principalAmount: number
  outstandingBalance: number
  status: string
}

export interface CollectionSheetItem {
  clientId: string
  clientName: string
  loanId: string
  loanAccountNo: string
  dueAmount: number
  paidAmount: number
}

export interface CollectionSheet {
  id?: string
  meetingDate: string
  items: CollectionSheetItem[]
}

export interface GenerateCollectionSheetRequest {
  groupId: string
  meetingDate: string
}

// ── Centers ────────────────────────────────────────────────────────────────────
export type CenterStatus = 'PENDING' | 'ACTIVE' | 'CLOSED'

export interface Center {
  id: string
  name: string
  externalId?: string
  status: CenterStatus
  officeId: string
  officeName: string
  staffId?: string
  staffName?: string
  activationDate?: string
  submittedOnDate: string
}

export interface CreateCenterRequest {
  name: string
  officeId: string
  externalId?: string
  staffId?: string
}

export interface CenterGroup {
  id: string
  name: string
  status: GroupStatus
}

export interface CenterMember {
  id: string
  displayName: string
  accountNo: string
  groupId: string
  groupName: string
}
