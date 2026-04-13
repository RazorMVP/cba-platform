import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from '../../core/api/api.service';

// ── Shared ─────────────────────────────────────────────────────────────────────
export type GroupStatus = 'PENDING' | 'ACTIVE' | 'INACTIVE' | 'CLOSED';

export interface GroupMember {
  customerId: string;
  customerName: string;
  accountNo: string;
  joinedDate: string;
}

export interface CollectionSheetItem {
  customerId: string;
  customerName: string;
  loanId: string;
  loanAccountNo: string;
  dueAmount: number;
  paidAmount: number;
  outstanding: number;
}

export interface CollectionSheet {
  id: string;
  groupId: string;
  meetingDate: string;
  items: CollectionSheetItem[];
  totalDue: number;
  totalCollected: number;
}

export interface GlimAccount {
  id: string;
  groupId: string;
  accountNo: string;
  totalAmount: number;
  members: GlimMember[];
  status: string;
}

export interface GlimMember {
  customerId: string;
  customerName: string;
  individualAmount: number;
}

// ── Groups ────────────────────────────────────────────────────────────────────
export interface Group {
  id: string;
  name: string;
  externalId: string | null;
  centerId: string | null;
  centerName: string | null;
  officeId: string;
  officeName: string;
  staffId: string | null;
  staffName: string | null;
  status: GroupStatus;
  activationDate: string | null;
  memberCount: number;
}

export interface CreateGroupRequest {
  name: string;
  externalId?: string;
  centerId?: string;
  officeId: string;
  staffId?: string;
  activationDate?: string;
}

// ── Centers ───────────────────────────────────────────────────────────────────
export interface Center {
  id: string;
  name: string;
  externalId: string | null;
  officeId: string;
  officeName: string;
  staffId: string | null;
  staffName: string | null;
  status: GroupStatus;
  activationDate: string | null;
  groupCount: number;
}

export interface CreateCenterRequest {
  name: string;
  externalId?: string;
  officeId: string;
  staffId?: string;
  activationDate?: string;
}

@Injectable({ providedIn: 'root' })
export class GroupsService {
  private readonly api = inject(ApiService);

  // ── Groups ─────────────────────────────────────────────────────────────────
  listGroups(status?: GroupStatus): Observable<Group[]> {
    const params = status ? { status } : undefined;
    return this.api.get<Group[]>('/api/v1/groups', params);
  }

  getGroup(id: string): Observable<Group> {
    return this.api.get<Group>(`/api/v1/groups/${id}`);
  }

  createGroup(req: CreateGroupRequest): Observable<Group> {
    return this.api.post<Group>('/api/v1/groups', req);
  }

  updateGroup(id: string, req: CreateGroupRequest): Observable<Group> {
    return this.api.put<Group>(`/api/v1/groups/${id}`, req);
  }

  activateGroup(id: string): Observable<Group> {
    return this.api.command<Group>(`/api/v1/groups/${id}`, 'activate');
  }

  getGroupMembers(id: string): Observable<GroupMember[]> {
    return this.api.get<GroupMember[]>(`/api/v1/groups/${id}/members`);
  }

  addMember(groupId: string, customerId: string): Observable<void> {
    return this.api.post<void>(`/api/v1/groups/${groupId}/members/${customerId}`, {});
  }

  removeMember(groupId: string, customerId: string): Observable<void> {
    return this.api.delete<void>(`/api/v1/groups/${groupId}/members/${customerId}`);
  }

  generateCollectionSheet(groupId: string, meetingDate: string): Observable<CollectionSheet> {
    return this.api.post<CollectionSheet>('/api/v1/collectionsheets', { groupId, meetingDate });
  }

  getGlimAccounts(groupId: string): Observable<GlimAccount[]> {
    return this.api.get<GlimAccount[]>(`/api/v1/groups/${groupId}/glimaccounts`);
  }

  assignStaff(groupId: string, staffId: string): Observable<Group> {
    return this.api.post<Group>(`/api/v1/groups/${groupId}/assignstaff?staffId=${staffId}`, {});
  }

  unassignStaff(groupId: string): Observable<Group> {
    return this.api.delete<Group>(`/api/v1/groups/${groupId}/assignstaff`);
  }

  // ── Centers ────────────────────────────────────────────────────────────────
  listCenters(status?: GroupStatus): Observable<Center[]> {
    const params = status ? { status } : undefined;
    return this.api.get<Center[]>('/api/v1/centers', params);
  }

  getCenter(id: string): Observable<Center> {
    return this.api.get<Center>(`/api/v1/centers/${id}`);
  }

  createCenter(req: CreateCenterRequest): Observable<Center> {
    return this.api.post<Center>('/api/v1/centers', req);
  }

  updateCenter(id: string, req: CreateCenterRequest): Observable<Center> {
    return this.api.put<Center>(`/api/v1/centers/${id}`, req);
  }

  activateCenter(id: string): Observable<Center> {
    return this.api.command<Center>(`/api/v1/centers/${id}`, 'activate');
  }

  getCenterGroups(centerId: string): Observable<Group[]> {
    return this.api.get<Group[]>(`/api/v1/centers/${centerId}/groups`);
  }

  getCenterMembers(centerId: string): Observable<GroupMember[]> {
    return this.api.get<GroupMember[]>(`/api/v1/centers/${centerId}/members`);
  }
}
