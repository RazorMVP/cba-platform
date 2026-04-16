import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge';
import {
  AdminService, PlatformUser, CreateUserRequest, Role, Office,
} from './admin.service';

type ModalType = 'create' | 'delete' | null;

@Component({
  selector: 'app-users',
  standalone: true,
  imports: [CommonModule, FormsModule, StatusBadgeComponent],
  templateUrl: './users.html',
  styleUrl: './users.scss',
})
export class UsersComponent implements OnInit {
  private readonly svc = inject(AdminService);

  users:   PlatformUser[] = [];
  roles:   Role[]         = [];
  offices: Office[]       = [];
  loading = true;
  error   = '';

  searchQuery = '';
  get filtered(): PlatformUser[] {
    const q = this.searchQuery.toLowerCase();
    return q
      ? this.users.filter(u =>
          u.username.toLowerCase().includes(q) ||
          u.email.toLowerCase().includes(q) ||
          `${u.firstname} ${u.lastname}`.toLowerCase().includes(q))
      : this.users;
  }

  // ── Modal ──────────────────────────────────────────────────────────────────
  activeModal:  ModalType = null;
  deletingId    = '';
  deletingName  = '';
  modalWorking  = false;
  modalError    = '';

  form: CreateUserRequest = this.blankForm();
  selectedRoleIds: string[] = [];

  ngOnInit(): void {
    this.loadAll();
  }

  loadAll(): void {
    this.svc.listUsers().subscribe({
      next: list => { this.users = list; this.loading = false; },
      error: () => { this.error = 'Failed to load users.'; this.loading = false; },
    });
    this.svc.listRoles().subscribe({ next: r => this.roles = r });
    this.svc.listOffices().subscribe({ next: o => this.offices = o });
  }

  openCreateModal(): void {
    this.activeModal     = 'create';
    this.form            = this.blankForm();
    this.selectedRoleIds = [];
    this.modalWorking    = false;
    this.modalError      = '';
  }

  openDeleteModal(u: PlatformUser): void {
    this.activeModal  = 'delete';
    this.deletingId   = u.id;
    this.deletingName = u.username;
    this.modalWorking = false;
    this.modalError   = '';
  }

  toggleRoleId(id: string): void {
    this.selectedRoleIds = this.selectedRoleIds.includes(id)
      ? this.selectedRoleIds.filter(r => r !== id)
      : [...this.selectedRoleIds, id];
  }

  submitCreate(): void {
    if (!this.form.username || !this.form.email || !this.form.password || !this.form.officeId) return;
    this.modalWorking = true;
    const req: CreateUserRequest = { ...this.form, roleIds: this.selectedRoleIds };
    this.svc.createUser(req).subscribe({
      next: u => {
        this.users = [...this.users, u];
        this.modalWorking = false;
        this.activeModal  = null;
      },
      error: () => { this.modalError = 'Failed to create user.'; this.modalWorking = false; },
    });
  }

  submitDelete(): void {
    this.modalWorking = true;
    this.svc.deleteUser(this.deletingId).subscribe({
      next: () => {
        this.users = this.users.filter(u => u.id !== this.deletingId);
        this.modalWorking = false;
        this.activeModal  = null;
      },
      error: () => { this.modalError = 'Cannot delete user.'; this.modalWorking = false; },
    });
  }

  toggleEnabled(u: PlatformUser): void {
    const call = u.enabled ? this.svc.disableUser(u.id) : this.svc.enableUser(u.id);
    call.subscribe({
      next: updated => {
        this.users = this.users.map(x => x.id === updated.id ? updated : x);
      },
    });
  }

  closeModal(): void { if (!this.modalWorking) this.activeModal = null; }

  roleNames(u: PlatformUser): string {
    return u.roles.map(r => r.name).join(', ') || '—';
  }

  private blankForm(): CreateUserRequest {
    return { username: '', firstname: '', lastname: '', email: '', password: '', officeId: '', roleIds: [] };
  }
}
