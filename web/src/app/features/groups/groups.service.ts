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
    return this.api.get<Group[]>('/groups', params);
  }

  getGroup(id: string): Observable<Group> {
    return this.api.get<Group>(`/groups/${id}`);
  }

  createGroup(req: CreateGroupRequest): Observable<Group> {
    return this.api.post<Group>('/groups', req);
  }

  updateGroup(id: string, req: CreateGroupRequest): Observable<Group> {
    return this.api.put<Group>(`/groups/${id}`, req);
  }

  activateGroup(id: string): Observable<Group> {
    return this.api.command<Group>(`/groups/${id}`, 'activate');
  }

  getGroupMembers(id: string): Observable<GroupMember[]> {
    return this.api.get<GroupMember[]>(`/groups/${id}/members`);
  }

  addMember(groupId: string, customerId: string): Observable<void> {
    return this.api.post<void>(`/groups/${groupId}/members/${customerId}`, {});
  }

  removeMember(groupId: string, customerId: string): Observable<void> {
    return this.api.delete<void>(`/groups/${groupId}/members/${customerId}`);
  }

  generateCollectionSheet(groupId: string, meetingDate: string): Observable<CollectionSheet> {
    return this.api.post<CollectionSheet>('/collectionsheets', { groupId, meetingDate });
  }

  getGlimAccounts(groupId: string): Observable<GlimAccount[]> {
    return this.api.get<GlimAccount[]>(`/groups/${groupId}/glimaccounts`);
  }

  assignStaff(groupId: string, staffId: string): Observable<Group> {
    return this.api.post<Group>(`/groups/${groupId}/assignstaff?staffId=${staffId}`, {});
  }

  unassignStaff(groupId: string): Observable<Group> {
    return this.api.delete<Group>(`/groups/${groupId}/assignstaff`);
  }

  // ── Centers ────────────────────────────────────────────────────────────────
  listCenters(status?: GroupStatus): Observable<Center[]> {
    const params = status ? { status } : undefined;
    return this.api.get<Center[]>('/centers', params);
  }

  getCenter(id: string): Observable<Center> {
    return this.api.get<Center>(`/centers/${id}`);
  }

  createCenter(req: CreateCenterRequest): Observable<Center> {
    return this.api.post<Center>('/centers', req);
  }

  updateCenter(id: string, req: CreateCenterRequest): Observable<Center> {
    return this.api.put<Center>(`/centers/${id}`, req);
  }

  activateCenter(id: string): Observable<Center> {
    return this.api.command<Center>(`/centers/${id}`, 'activate');
  }

  getCenterGroups(centerId: string): Observable<Group[]> {
    return this.api.get<Group[]>(`/centers/${centerId}/groups`);
  }

  getCenterMembers(centerId: string): Observable<GroupMember[]> {
    return this.api.get<GroupMember[]>(`/centers/${centerId}/members`);
  }
}
