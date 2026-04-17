import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  AdminService, StandingInstruction, CreateStandingInstructionRequest,
  InstructionType, InstructionPriority, RecurrenceType,
} from './admin.service';

@Component({
  selector: 'app-standing-instructions',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './standing-instructions.html',
  styleUrl: './standing-instructions.scss',
})
export class StandingInstructionsComponent implements OnInit {
  private readonly svc = inject(AdminService);

  instructions: StandingInstruction[] = [];
  loading = true;
  error   = '';

  page     = 0;
  pageSize = 20;
  total    = 0;

  activeModal: 'create' | 'edit' | 'delete' | 'toggle' | null = null;
  editTarget:   StandingInstruction | null = null;
  actionTarget: StandingInstruction | null = null;
  working    = false;
  modalError = '';

  form: CreateStandingInstructionRequest = this.blank();

  readonly instructionTypes: InstructionType[]  = ['FIXED', 'OUTSTANDING_BALANCE'];
  readonly priorities: InstructionPriority[]    = ['URGENT', 'HIGH', 'MEDIUM', 'LOW'];
  readonly recurrenceTypes: RecurrenceType[]     = ['PERIODIC_RECURRENCE', 'AS_PER_DUES'];

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.error = '';
    this.svc.listStandingInstructions(this.page).subscribe({
      next: res => { this.instructions = res.content; this.total = res.totalElements; this.loading = false; },
      error: () => { this.error = 'Failed to load standing instructions.'; this.loading = false; },
    });
  }

  openCreate(): void {
    this.form = this.blank();
    this.editTarget = null;
    this.modalError = '';
    this.working = false;
    this.activeModal = 'create';
  }

  openEdit(i: StandingInstruction): void {
    this.form = {
      name: i.name, fromAccountId: i.fromAccountId, toAccountId: i.toAccountId,
      instructionType: i.instructionType, priority: i.priority,
      recurrenceType: i.recurrenceType, recurrenceFrequency: i.recurrenceFrequency ?? 1,
      amount: i.amount ?? 0, validFrom: i.validFrom ?? '', validTill: i.validTill ?? '',
    };
    this.editTarget = i;
    this.modalError = '';
    this.working = false;
    this.activeModal = 'edit';
  }

  openDelete(i: StandingInstruction): void {
    this.actionTarget = i;
    this.working = false;
    this.activeModal = 'delete';
  }

  openToggle(i: StandingInstruction): void {
    this.actionTarget = i;
    this.working = false;
    this.activeModal = 'toggle';
  }

  closeModal(): void { this.activeModal = null; }

  save(): void {
    this.working = true;
    this.modalError = '';
    const obs = this.activeModal === 'edit' && this.editTarget
      ? this.svc.updateStandingInstruction(this.editTarget.id, this.form)
      : this.svc.createStandingInstruction(this.form);
    obs.subscribe({
      next: () => { this.closeModal(); this.load(); },
      error: () => { this.modalError = 'Save failed. Please try again.'; this.working = false; },
    });
  }

  confirmDelete(): void {
    if (!this.actionTarget) return;
    this.working = true;
    this.svc.deleteStandingInstruction(this.actionTarget.id).subscribe({
      next: () => { this.closeModal(); this.load(); },
      error: () => { this.working = false; },
    });
  }

  confirmToggle(): void {
    if (!this.actionTarget) return;
    this.working = true;
    const obs = this.actionTarget.status === 'ACTIVE'
      ? this.svc.disableStandingInstruction(this.actionTarget.id)
      : this.svc.enableStandingInstruction(this.actionTarget.id);
    obs.subscribe({
      next: () => { this.closeModal(); this.load(); },
      error: () => { this.working = false; },
    });
  }

  statusVariant(s: StandingInstruction): string {
    return s.status === 'ACTIVE' ? 'success' : s.status === 'DISABLED' ? 'warning' : 'neutral';
  }

  priorityClass(p: InstructionPriority): string {
    return ({ URGENT: 'urgent', HIGH: 'high', MEDIUM: 'medium', LOW: 'low' })[p] ?? '';
  }

  get totalPages(): number { return Math.ceil(this.total / this.pageSize); }
  prev(): void { if (this.page > 0) { this.page--; this.load(); } }
  next(): void { if (this.page < this.totalPages - 1) { this.page++; this.load(); } }

  private blank(): CreateStandingInstructionRequest {
    return {
      name: '', fromAccountId: '', toAccountId: '',
      instructionType: 'FIXED', priority: 'MEDIUM',
      recurrenceType: 'PERIODIC_RECURRENCE', recurrenceFrequency: 1,
      amount: 0, validFrom: '', validTill: '',
    };
  }
}
