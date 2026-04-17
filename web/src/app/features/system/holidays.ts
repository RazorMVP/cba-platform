import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge';
import { SystemService, Holiday, CreateHolidayRequest } from './system.service';
import { PageResponse } from '../../core/models/api-response.model';

type ModalType = 'create' | 'delete' | 'activate' | null;
type RepaymentSchedulingType = Holiday['repaymentSchedulingType'];

@Component({
  selector: 'app-holidays',
  standalone: true,
  imports: [CommonModule, FormsModule, StatusBadgeComponent],
  templateUrl: './holidays.html',
  styleUrl: './holidays.scss',
})
export class HolidaysComponent implements OnInit {
  private readonly svc = inject(SystemService);

  holidays: Holiday[] = [];
  loading = true;
  error = '';

  page      = 0;
  total     = 0;
  readonly pageSize = 20;

  activeModal: ModalType = null;
  modalWorking = false;
  modalError   = '';
  targetId: string | null = null;

  // Form
  formName       = '';
  formFrom       = '';
  formTo         = '';
  formScheduling: RepaymentSchedulingType = 'NEXT_WORKING_DAY';
  formRescheduled = '';

  readonly schedulingTypes: { value: RepaymentSchedulingType; label: string }[] = [
    { value: 'SAME_DAY',                   label: 'Same Day (no rescheduling)' },
    { value: 'NEXT_WORKING_DAY',           label: 'Next Working Day' },
    { value: 'PREVIOUS_WORKING_DAY',       label: 'Previous Working Day' },
    { value: 'NEXT_REPAYMENT_MEETING_DATE', label: 'Next Repayment Meeting Date' },
  ];

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.svc.listHolidays(this.page).subscribe({
      next:  p => { this.holidays = p.content; this.total = p.totalElements; this.loading = false; },
      error: () => { this.error = 'Failed to load holidays.'; this.loading = false; },
    });
  }

  openCreate(): void {
    this.formName       = '';
    this.formFrom       = '';
    this.formTo         = '';
    this.formScheduling = 'NEXT_WORKING_DAY';
    this.formRescheduled = '';
    this.modalError     = '';
    this.activeModal    = 'create';
  }

  openActivate(h: Holiday): void {
    this.targetId   = h.id;
    this.modalError = '';
    this.activeModal = 'activate';
  }

  openDelete(h: Holiday): void {
    this.targetId   = h.id;
    this.modalError = '';
    this.activeModal = 'delete';
  }

  closeModal(): void {
    if (!this.modalWorking) { this.activeModal = null; this.targetId = null; }
  }

  submitCreate(): void {
    if (!this.formName.trim() || !this.formFrom || !this.formTo) return;
    this.modalWorking = true;
    this.modalError   = '';
    const req: CreateHolidayRequest = {
      name:                     this.formName.trim(),
      fromDate:                 this.formFrom,
      toDate:                   this.formTo,
      repaymentSchedulingType:  this.formScheduling,
      rescheduledRepaymentDate: this.formRescheduled || undefined,
    };
    this.svc.createHoliday(req).subscribe({
      next:  () => { this.activeModal = null; this.modalWorking = false; this.load(); },
      error: () => { this.modalError = 'Failed to create holiday.'; this.modalWorking = false; },
    });
  }

  submitActivate(): void {
    if (!this.targetId) return;
    this.modalWorking = true;
    this.svc.activateHoliday(this.targetId).subscribe({
      next: updated => {
        const idx = this.holidays.findIndex(h => h.id === updated.id);
        if (idx !== -1) this.holidays[idx] = updated;
        this.activeModal = null; this.modalWorking = false;
      },
      error: () => { this.modalError = 'Activation failed.'; this.modalWorking = false; },
    });
  }

  submitDelete(): void {
    if (!this.targetId) return;
    this.modalWorking = true;
    this.svc.deleteHoliday(this.targetId).subscribe({
      next:  () => { this.activeModal = null; this.modalWorking = false; this.load(); },
      error: () => { this.modalError = 'Delete failed.'; this.modalWorking = false; },
    });
  }

  targetName(): string {
    return this.holidays.find(h => h.id === this.targetId)?.name ?? '';
  }

  schedulingLabel(type: RepaymentSchedulingType): string {
    return this.schedulingTypes.find(s => s.value === type)?.label ?? type;
  }

  statusVariant(s: Holiday['status']): 'success' | 'warning' {
    return s === 'ACTIVE' ? 'success' : 'warning';
  }

  get totalPages(): number { return Math.max(1, Math.ceil(this.total / this.pageSize)); }
  get startRow():   number { return this.total === 0 ? 0 : this.page * this.pageSize + 1; }
  get endRow():     number { return Math.min((this.page + 1) * this.pageSize, this.total); }

  prevPage(): void { if (this.page > 0) { this.page--; this.load(); } }
  nextPage(): void { if ((this.page + 1) * this.pageSize < this.total) { this.page++; this.load(); } }
}
