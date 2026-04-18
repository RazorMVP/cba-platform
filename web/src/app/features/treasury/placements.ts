import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, takeUntil } from 'rxjs';
import { TreasuryService, TreasuryPlacement, PlacementRequest } from './treasury.service';

@Component({
  selector: 'app-treasury-placements',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './placements.html',
  styleUrl: './placements.scss',
})
export class TreasuryPlacementsComponent implements OnInit, OnDestroy {
  private readonly destroy$ = new Subject<void>();

  placements: TreasuryPlacement[] = [];
  loading = true;
  error = '';

  activeModal: 'create' | 'edit' | 'delete' | 'command' | null = null;
  modalError = '';
  modalWorking = false;

  // form state
  formReference = '';
  formCounterparty = '';
  formBic = '';
  formType = 'FIXED_DEPOSIT';
  formPrincipal: number | null = null;
  formRate: number | null = null;
  formCurrency = 'USD';
  formStartDate = '';
  formMaturityDate = '';
  formExpectedReturn: number | null = null;
  formNotes = '';

  editingId = '';
  deletingId = '';
  deletingRef = '';
  commandId = '';
  commandVerb = '';
  commandLabel = '';

  readonly placementTypes = ['FIXED_DEPOSIT', 'TREASURY_BILL', 'BOND', 'CALL_MONEY', 'REPO'];
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
    this.svc.listPlacements().pipe(takeUntil(this.destroy$)).subscribe({
      next: p => { this.placements = p; this.loading = false; },
      error: e => { this.error = e?.error?.errors?.[0]?.message ?? 'Failed to load placements'; this.loading = false; },
    });
  }

  openCreate(): void {
    this.resetForm();
    this.editingId = '';
    this.modalError = '';
    this.activeModal = 'create';
  }

  openEdit(p: TreasuryPlacement): void {
    this.formReference = p.reference;
    this.formCounterparty = p.counterpartyName;
    this.formBic = p.counterpartyBic ?? '';
    this.formType = p.placementType;
    this.formPrincipal = p.principal;
    this.formRate = p.interestRate;
    this.formCurrency = p.currencyCode;
    this.formStartDate = p.startDate;
    this.formMaturityDate = p.maturityDate;
    this.formExpectedReturn = p.expectedReturn ?? null;
    this.formNotes = p.notes ?? '';
    this.editingId = p.id;
    this.modalError = '';
    this.activeModal = 'edit';
  }

  openDelete(p: TreasuryPlacement): void {
    this.deletingId = p.id;
    this.deletingRef = p.reference;
    this.modalError = '';
    this.activeModal = 'delete';
  }

  openCommand(p: TreasuryPlacement, cmd: string): void {
    this.commandId = p.id;
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
    if (!this.formReference || !this.formCounterparty || !this.formPrincipal || !this.formRate || !this.formStartDate || !this.formMaturityDate) {
      this.modalError = 'Please fill in all required fields.';
      return;
    }
    const req: PlacementRequest = {
      reference: this.formReference,
      counterpartyName: this.formCounterparty,
      counterpartyBic: this.formBic || undefined,
      placementType: this.formType,
      principal: this.formPrincipal,
      interestRate: this.formRate,
      currencyCode: this.formCurrency,
      startDate: this.formStartDate,
      maturityDate: this.formMaturityDate,
      expectedReturn: this.formExpectedReturn ?? undefined,
      notes: this.formNotes || undefined,
    };
    this.modalWorking = true;
    this.modalError = '';
    const obs = this.activeModal === 'create'
      ? this.svc.createPlacement(req)
      : this.svc.updatePlacement(this.editingId, req);
    obs.pipe(takeUntil(this.destroy$)).subscribe({
      next: () => { this.activeModal = null; this.modalWorking = false; this.load(); },
      error: e => { this.modalError = e?.error?.errors?.[0]?.message ?? 'Save failed'; this.modalWorking = false; },
    });
  }

  submitDelete(): void {
    this.modalWorking = true;
    this.svc.deletePlacement(this.deletingId).pipe(takeUntil(this.destroy$)).subscribe({
      next: () => { this.activeModal = null; this.modalWorking = false; this.load(); },
      error: e => { this.modalError = e?.error?.errors?.[0]?.message ?? 'Delete failed'; this.modalWorking = false; },
    });
  }

  submitCommand(): void {
    this.modalWorking = true;
    this.svc.commandPlacement(this.commandId, this.commandVerb).pipe(takeUntil(this.destroy$)).subscribe({
      next: () => { this.activeModal = null; this.modalWorking = false; this.load(); },
      error: e => { this.modalError = e?.error?.errors?.[0]?.message ?? 'Command failed'; this.modalWorking = false; },
    });
  }

  availableCommands(p: TreasuryPlacement): { label: string; cmd: string }[] {
    if (p.status === 'PENDING') return [{ label: 'Activate', cmd: 'activate' }];
    if (p.status === 'ACTIVE') return [{ label: 'Mature', cmd: 'mature' }, { label: 'Cancel', cmd: 'cancel' }];
    return [];
  }

  statusClass(s: string): string {
    return { PENDING: 'badge-warning', ACTIVE: 'badge-success', MATURED: 'badge-info', CANCELLED: 'badge-neutral' }[s] ?? '';
  }

  typeLabel(t: string): string {
    return t.replace(/_/g, ' ');
  }

  formatAmount(n: number, ccy: string): string {
    return new Intl.NumberFormat('en-US', { style: 'currency', currency: ccy, maximumFractionDigits: 2 }).format(n);
  }

  private resetForm(): void {
    this.formReference = '';
    this.formCounterparty = '';
    this.formBic = '';
    this.formType = 'FIXED_DEPOSIT';
    this.formPrincipal = null;
    this.formRate = null;
    this.formCurrency = 'USD';
    this.formStartDate = '';
    this.formMaturityDate = '';
    this.formExpectedReturn = null;
    this.formNotes = '';
  }
}
