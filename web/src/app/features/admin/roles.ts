import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge';
import {
  AdminService, Role, Permission, CreateRoleRequest,
} from './admin.service';

type ModalType = 'create' | 'edit' | 'permissions' | 'delete' | null;

@Component({
  selector: 'app-roles',
  standalone: true,
  imports: [CommonModule, FormsModule, StatusBadgeComponent],
  templateUrl: './roles.html',
  styleUrl: './roles.scss',
})
export class RolesComponent implements OnInit {
  private readonly svc = inject(AdminService);

  roles:    Role[]       = [];
  allPerms: Permission[] = [];
  loading = true;
  error   = '';

  // ── Modal ──────────────────────────────────────────────────────────────────
  activeModal:  ModalType = null;
  editingId     = '';
  deletingName  = '';
  modalWorking  = false;
  modalError    = '';

  formName        = '';
  formDescription = '';
  selectedPermIds: string[] = [];

  get permGroups(): string[] {
    return [...new Set(this.allPerms.map(p => p.grouping))].sort();
  }

  permsByGroup(group: string): Permission[] {
    return this.allPerms.filter(p => p.grouping === group);
  }

  groupAllSelected(group: string): boolean {
    return this.permsByGroup(group).every(p => this.selectedPermIds.includes(p.id));
  }

  ngOnInit(): void {
    this.svc.listRoles().subscribe({
      next: list => { this.roles = list; this.loading = false; },
      error: () => { this.error = 'Failed to load roles.'; this.loading = false; },
    });
    this.svc.listPermissions().subscribe({ next: p => this.allPerms = p });
  }

  openCreateModal(): void {
    this.activeModal = 'create'; this.editingId = '';
    this.formName = ''; this.formDescription = ''; this.selectedPermIds = [];
    this.modalWorking = false; this.modalError = '';
  }

  openEditModal(r: Role): void {
    this.activeModal = 'edit'; this.editingId = r.id;
    this.formName = r.name; this.formDescription = r.description;
    this.modalWorking = false; this.modalError = '';
  }

  openPermissionsModal(r: Role): void {
    this.activeModal = 'permissions'; this.editingId = r.id;
    this.formName = r.name;
    this.selectedPermIds = r.permissions.map(p => p.id);
    this.modalWorking = false; this.modalError = '';
  }

  openDeleteModal(r: Role): void {
    this.activeModal = 'delete'; this.editingId = r.id;
    this.deletingName = r.name; this.modalWorking = false; this.modalError = '';
  }

  togglePermId(id: string): void {
    this.selectedPermIds = this.selectedPermIds.includes(id)
      ? this.selectedPermIds.filter(p => p !== id)
      : [...this.selectedPermIds, id];
  }

  toggleGroup(group: string, checked: boolean): void {
    const ids = this.permsByGroup(group).map(p => p.id);
    if (checked) {
      this.selectedPermIds = [...new Set([...this.selectedPermIds, ...ids])];
    } else {
      this.selectedPermIds = this.selectedPermIds.filter(id => !ids.includes(id));
    }
  }

  submitRole(): void {
    if (!this.formName) return;
    this.modalWorking = true;
    const req: CreateRoleRequest = { name: this.formName, description: this.formDescription };
    const call = this.activeModal === 'create'
      ? this.svc.createRole(req)
      : this.svc.updateRole(this.editingId, req);
    call.subscribe({
      next: r => {
        this.roles = this.activeModal === 'create'
          ? [...this.roles, r]
          : this.roles.map(x => x.id === r.id ? r : x);
        this.modalWorking = false; this.activeModal = null;
      },
      error: () => { this.modalError = 'Failed to save role.'; this.modalWorking = false; },
    });
  }

  submitPermissions(): void {
    this.modalWorking = true;
    this.svc.updateRolePermissions(this.editingId, { permissionIds: this.selectedPermIds }).subscribe({
      next: r => {
        this.roles = this.roles.map(x => x.id === r.id ? r : x);
        this.modalWorking = false; this.activeModal = null;
      },
      error: () => { this.modalError = 'Failed to update permissions.'; this.modalWorking = false; },
    });
  }

  submitDelete(): void {
    this.modalWorking = true;
    this.svc.deleteRole(this.editingId).subscribe({
      next: () => {
        this.roles = this.roles.filter(r => r.id !== this.editingId);
        this.modalWorking = false; this.activeModal = null;
      },
      error: () => { this.modalError = 'Cannot delete role in use.'; this.modalWorking = false; },
    });
  }

  closeModal(): void { if (!this.modalWorking) this.activeModal = null; }

  permCount(r: Role): number { return r.permissions?.length ?? 0; }
}
