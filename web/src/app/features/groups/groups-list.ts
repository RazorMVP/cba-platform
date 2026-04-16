import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge';
import { GroupsService, Group, GroupStatus, CreateGroupRequest } from './groups.service';
import { AdminService } from '../admin/admin.service';

type ModalType = 'create' | null;

@Component({
  selector: 'app-groups-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, StatusBadgeComponent],
  templateUrl: './groups-list.html',
  styleUrl: './groups-list.scss',
})
export class GroupsListComponent implements OnInit {
  private readonly svc     = inject(GroupsService);
  private readonly adminSvc = inject(AdminService);

  groups:  Group[]   = [];
  offices: any[]     = [];
  loading  = true;
  error    = '';

  searchQuery    = '';
  statusFilter: GroupStatus | '' = '';
  readonly statuses: Array<{ label: string; value: GroupStatus | '' }> = [
    { label: 'All',      value: '' },
    { label: 'Pending',  value: 'PENDING' },
    { label: 'Active',   value: 'ACTIVE' },
    { label: 'Inactive', value: 'INACTIVE' },
    { label: 'Closed',   value: 'CLOSED' },
  ];

  get filtered(): Group[] {
    const q = this.searchQuery.toLowerCase();
    return this.groups.filter(g =>
      (!this.statusFilter || g.status === this.statusFilter) &&
      (!q || g.name.toLowerCase().includes(q)));
  }

  // ── Modal ──────────────────────────────────────────────────────────────────
  activeModal: ModalType = null;
  modalWorking = false;
  modalError   = '';
  form: CreateGroupRequest = this.blankForm();

  ngOnInit(): void {
    this.svc.listGroups().subscribe({
      next: list => { this.groups = list; this.loading = false; },
      error: () => { this.error = 'Failed to load groups.'; this.loading = false; },
    });
    this.adminSvc.listOffices().subscribe({ next: o => this.offices = o });
  }

  openCreateModal(): void {
    this.activeModal = 'create'; this.form = this.blankForm();
    this.modalWorking = false; this.modalError = '';
  }

  submitCreate(): void {
    if (!this.form.name || !this.form.officeId) return;
    this.modalWorking = true;
    this.svc.createGroup(this.form).subscribe({
      next: g => { this.groups = [...this.groups, g]; this.modalWorking = false; this.activeModal = null; },
      error: () => { this.modalError = 'Failed to create group.'; this.modalWorking = false; },
    });
  }

  closeModal(): void { if (!this.modalWorking) this.activeModal = null; }

  statusVariant(s: GroupStatus): 'success' | 'warning' | 'neutral' {
    if (s === 'ACTIVE') return 'success';
    if (s === 'PENDING') return 'warning';
    return 'neutral';
  }

  private blankForm(): CreateGroupRequest {
    return { name: '', officeId: '' };
  }
}
