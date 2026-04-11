import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge';
import { GroupsService, Center, GroupStatus, CreateCenterRequest } from './groups.service';
import { AdminService } from '../admin/admin.service';

type ModalType = 'create' | null;

@Component({
  selector: 'app-centers-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, StatusBadgeComponent],
  templateUrl: './centers-list.html',
  styleUrl: './centers-list.scss',
})
export class CentersListComponent implements OnInit {
  private readonly svc      = inject(GroupsService);
  private readonly adminSvc = inject(AdminService);

  centers: Center[] = [];
  offices: any[]    = [];
  loading  = true;
  error    = '';

  searchQuery   = '';
  statusFilter: GroupStatus | '' = '';
  readonly statuses: Array<{ label: string; value: GroupStatus | '' }> = [
    { label: 'All', value: '' }, { label: 'Pending', value: 'PENDING' },
    { label: 'Active', value: 'ACTIVE' }, { label: 'Closed', value: 'CLOSED' },
  ];

  get filtered(): Center[] {
    const q = this.searchQuery.toLowerCase();
    return this.centers.filter(c =>
      (!this.statusFilter || c.status === this.statusFilter) &&
      (!q || c.name.toLowerCase().includes(q)));
  }

  activeModal: ModalType = null;
  modalWorking = false;
  modalError   = '';
  form: CreateCenterRequest = this.blankForm();

  ngOnInit(): void {
    this.svc.listCenters().subscribe({
      next: list => { this.centers = list; this.loading = false; },
      error: () => { this.error = 'Failed to load centers.'; this.loading = false; },
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
    this.svc.createCenter(this.form).subscribe({
      next: c => { this.centers = [...this.centers, c]; this.modalWorking = false; this.activeModal = null; },
      error: () => { this.modalError = 'Failed to create center.'; this.modalWorking = false; },
    });
  }

  closeModal(): void { if (!this.modalWorking) this.activeModal = null; }

  statusVariant(s: GroupStatus): 'success' | 'warning' | 'neutral' {
    if (s === 'ACTIVE') return 'success';
    if (s === 'PENDING') return 'warning';
    return 'neutral';
  }

  private blankForm(): CreateCenterRequest { return { name: '', officeId: '' }; }
}
