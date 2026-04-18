import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, takeUntil } from 'rxjs';
import { TreasuryService, TreasuryInterbankPosition, InterbankRequest } from './treasury.service';

@Component({
  selector: 'app-treasury-interbank',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './interbank.html',
  styleUrl: './interbank.scss',
})
export class TreasuryInterbankComponent implements OnInit, OnDestroy {
  private readonly destroy$ = new Subject<void>();

  positions: TreasuryInterbankPosition[] = [];
  loading = true;
  error = '';

  activeModal: 'create' | 'edit' | 'delete' | 'command' | null = null;
  modalError = '';
  modalWorking = false;

  formReference = '';
  formCounterparty = '';
  formBic = '';
  formDirection = 'LENDING';
  formAmount: number | null = null;
  formCurrency = 'USD';
  formRate: number | null = null;
  formStartDate = '';
  formMaturityDate = '';
  formNotes = '';

  editingId = '';
  deletingId = '';
  deletingRef = '';
  commandId = '';
  commandVerb = '';
  commandLabel = '';

  readonly currencies = ['USD', 'EUR', 'GBP', 'KES', 'GHS', 'NGN'];

  constructor(private svc: TreasuryService) {}

  ngOnInit(): void {
    this.load();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  load(): void {
    this.loading = true;
    this.error = '';
    this.svc.listPositions().pipe(takeUntil(this.destroy$)).subscribe({
      next: p => { this.positions = p; this.loading = false; },
      error: e => { this.error = e?.error?.errors?.[0]?.message ?? 'Failed to load positions'; this.loading = false; },
    });
  }

  openCreate(): void {
    this.resetForm();
    this.editingId = '';
    this.modalError = '';
    this.activeModal = 'create';
  }

  openEdit(pos: TreasuryInterbankPosition): void {
    this.formReference = pos.reference;
    this.formCounterparty = pos.counterpartyName;
    this.formBic = pos.counterpartyBic ?? '';
    this.formDirection = pos.direction;
    this.formAmount = pos.amount;
    this.formCurrency = pos.currencyCode;
    this.formRate = pos.interestRate;
    this.formStartDate = pos.startDate;
    this.formMaturityDate = pos.maturityDate ?? '';
    this.formNotes = pos.notes ?? '';
    this.editingId = pos.id;
    this.modalError = '';
    this.activeModal = 'edit';
  }

  openDelete(pos: TreasuryInterbankPosition): void {
    this.deletingId = pos.id;
    this.deletingRef = pos.reference;
    this.modalError = '';
    this.activeModal = 'delete';
  }

  openCommand(pos: TreasuryInterbankPosition, cmd: string): void {
    this.commandId = pos.id;
    this.commandVerb = cmd;
    this.commandLabel = cmd.charAt(0).toUpperCase() + cmd.slice(1);
    this.modalError = '';
    this.activeModal = 'command';
  }

  closeModal(): void {
    if (this.modalWorking) return;
    this.activeModal = null;
  }

  submitModal(): void {
    if (!this.formReference || !this.formCounterparty || !this.formAmount || !this.formRate || !this.formStartDate) {
      this.modalError = 'Please fill in all required fields.';
      return;
    }
    const req: InterbankRequest = {
      reference: this.formReference,
      counterpartyName: this.formCounterparty,
      counterpartyBic: this.formBic || undefined,
      direction: this.formDirection,
      amount: this.formAmount,
      currencyCode: this.formCurrency,
      interestRate: this.formRate,
      startDate: this.formStartDate,
      maturityDate: this.formMaturityDate || undefined,
      notes: this.formNotes || undefined,
    };
    this.modalWorking = true;
    this.modalError = '';
    const obs = this.activeModal === 'create'
      ? this.svc.createPosition(req)
      : this.svc.updatePosition(this.editingId, req);
    obs.pipe(takeUntil(this.destroy$)).subscribe({
      next: () => { this.activeModal = null; this.modalWorking = false; this.load(); },
      error: e => { this.modalError = e?.error?.errors?.[0]?.message ?? 'Save failed'; this.modalWorking = false; },
    });
  }

  submitDelete(): void {
    this.modalWorking = true;
    this.svc.deletePosition(this.deletingId).pipe(takeUntil(this.destroy$)).subscribe({
      next: () => { this.activeModal = null; this.modalWorking = false; this.load(); },
      error: e => { this.modalError = e?.error?.errors?.[0]?.message ?? 'Delete failed'; this.modalWorking = false; },
    });
  }

  submitCommand(): void {
    this.modalWorking = true;
    this.svc.commandPosition(this.commandId, this.commandVerb).pipe(takeUntil(this.destroy$)).subscribe({
      next: () => { this.activeModal = null; this.modalWorking = false; this.load(); },
      error: e => { this.modalError = e?.error?.errors?.[0]?.message ?? 'Command failed'; this.modalWorking = false; },
    });
  }

  availableCommands(pos: TreasuryInterbankPosition): { label: string; cmd: string }[] {
    if (pos.status === 'ACTIVE') return [{ label: 'Settle', cmd: 'settle' }, { label: 'Cancel', cmd: 'cancel' }];
    return [];
  }

  directionClass(d: string): string {
    return d === 'LENDING' ? 'dir-lending' : 'dir-borrowing';
  }

  statusClass(s: string): string {
    return { ACTIVE: 'badge-success', SETTLED: 'badge-info', CANCELLED: 'badge-neutral' }[s] ?? '';
  }

  formatAmount(n: number, ccy: string): string {
    return new Intl.NumberFormat('en-US', { style: 'currency', currency: ccy, maximumFractionDigits: 2 }).format(n);
  }

  private resetForm(): void {
    this.formReference = '';
    this.formCounterparty = '';
    this.formBic = '';
    this.formDirection = 'LENDING';
    this.formAmount = null;
    this.formCurrency = 'USD';
    this.formRate = null;
    this.formStartDate = '';
    this.formMaturityDate = '';
    this.formNotes = '';
  }
}
