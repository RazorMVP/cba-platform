import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AdminService, Staff, CreateStaffRequest, Office } from './admin.service';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge';

@Component({
  selector: 'app-staff',
  standalone: true,
  imports: [CommonModule, FormsModule, StatusBadgeComponent],
  templateUrl: './staff.html',
  styleUrl: './staff.scss',
})
export class StaffComponent implements OnInit {
  private readonly svc = inject(AdminService);

  staff:   Staff[]  = [];
  offices: Office[] = [];
  loading = true;
  error   = '';

  filterOfficeId = '';
  loanOfficerOnly = false;

  activeModal: 'create' | 'edit' | null = null;
  editTarget: Staff | null = null;
  working    = false;
  modalError = '';

  form: CreateStaffRequest = this.blank();

  ngOnInit(): void {
    this.svc.listOffices().subscribe({ next: o => this.offices = o });
    this.load();
  }

  load(): void {
    this.loading = true;
    this.error = '';
    this.svc.listStaff(this.filterOfficeId || undefined).subscribe({
      next: list => { this.staff = list; this.loading = false; },
      error: () => { this.error = 'Failed to load staff.'; this.loading = false; },
    });
  }

  onOfficeFilter(): void { this.load(); }

  get filteredStaff(): Staff[] {
    return this.loanOfficerOnly ? this.staff.filter(s => s.loanOfficer) : this.staff;
  }

  openCreate(): void {
    this.form = this.blank();
    this.editTarget = null;
    this.modalError = '';
    this.working = false;
    this.activeModal = 'create';
  }

  openEdit(s: Staff): void {
    this.form = {
      firstName: s.firstName, lastName: s.lastName,
      email: s.email ?? '', mobileNo: s.mobileNo ?? '',
      joiningDate: s.joiningDate ?? '', loanOfficer: s.loanOfficer,
      officeId: s.officeId,
    };
    this.editTarget = s;
    this.modalError = '';
    this.working = false;
    this.activeModal = 'edit';
  }

  closeModal(): void { this.activeModal = null; }

  save(): void {
    this.working = true;
    this.modalError = '';
    const obs = this.activeModal === 'edit' && this.editTarget
      ? this.svc.updateStaff(this.editTarget.id, this.form)
      : this.svc.createStaff(this.form);
    obs.subscribe({
      next: () => { this.closeModal(); this.load(); },
      error: () => { this.modalError = 'Save failed. Please try again.'; this.working = false; },
    });
  }

  officeName(id: string): string {
    return this.offices.find(o => o.id === id)?.name ?? id;
  }

  private blank(): CreateStaffRequest {
    return { firstName: '', lastName: '', email: '', mobileNo: '', joiningDate: '', loanOfficer: false, officeId: '' };
  }
}
