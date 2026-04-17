import { Component, inject, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, debounceTime, distinctUntilChanged } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { StatusBadgeComponent } from '../../../shared/components/status-badge/status-badge';
import {
  ProductService,
  ChargeDefinition,
  ChargeCreateRequest,
  ChargeAppliesTo,
  ChargeTimeType,
  ChargeCalculation,
} from '../product.service';

interface ChargeForm {
  name: string;
  currencyCode: string;
  chargeAppliesTo: ChargeAppliesTo | '';
  chargeTimeType: ChargeTimeType | '';
  chargeCalculation: ChargeCalculation | '';
  amount: number | null;
  penalty: boolean;
  active: boolean;
}

const BLANK_FORM = (): ChargeForm => ({
  name: '', currencyCode: 'USD',
  chargeAppliesTo: '', chargeTimeType: '', chargeCalculation: 'FLAT',
  amount: null, penalty: false, active: true,
});

const TIME_TYPES_BY_APPLIES: Record<ChargeAppliesTo, ChargeTimeType[]> = {
  LOAN:    ['DISBURSEMENT', 'SPECIFIED_DUE_DATE', 'INSTALLMENT_FEE', 'OVERDUE_INSTALLMENT'],
  SAVINGS: ['WITHDRAWAL_FEE', 'ANNUAL_FEE', 'MONTHLY_FEE', 'SAVINGS_ACTIVATION'],
  CLIENT:  ['SPECIFIED_DUE_DATE', 'ANNUAL_FEE', 'MONTHLY_FEE'],
  SHARE:   ['SHARE_PURCHASE', 'SPECIFIED_DUE_DATE'],
};

@Component({
  selector: 'app-charges',
  standalone: true,
  imports: [CommonModule, FormsModule, StatusBadgeComponent],
  templateUrl: './charges.html',
  styleUrl: './charges.scss',
})
export class ChargesComponent implements OnInit, OnDestroy {
  private readonly svc  = inject(ProductService);
  private readonly destroy$ = new Subject<void>();
  private readonly search$  = new Subject<string>();

  charges:   ChargeDefinition[] = [];
  filtered:  ChargeDefinition[] = [];
  loading    = true;
  saving     = false;

  // Filter
  searchQuery  = '';
  filterApplies: ChargeAppliesTo | '' = '';

  // Pagination (server-side)
  page      = 0;
  totalPages = 0;
  total      = 0;
  readonly pageSize = 20;

  // Modals
  showModal    = false;
  showDeleteModal = false;
  editTarget: ChargeDefinition | null = null;
  deleteTarget: ChargeDefinition | null = null;
  form: ChargeForm = BLANK_FORM();
  saveError = '';

  readonly appliesToOptions: ChargeAppliesTo[] = ['LOAN', 'SAVINGS', 'CLIENT', 'SHARE'];
  readonly calculationOptions: ChargeCalculation[] = [
    'FLAT', 'PERCENT_OF_AMOUNT', 'PERCENT_OF_INTEREST', 'PERCENT_OF_AMOUNT_AND_INTEREST',
  ];

  get timeTypeOptions(): ChargeTimeType[] {
    if (!this.form.chargeAppliesTo) return [];
    return TIME_TYPES_BY_APPLIES[this.form.chargeAppliesTo as ChargeAppliesTo] ?? [];
  }

  ngOnInit(): void {
    this.load();
    this.search$.pipe(debounceTime(250), distinctUntilChanged(), takeUntil(this.destroy$))
      .subscribe(() => { this.page = 0; this.load(); });
  }

  ngOnDestroy(): void { this.destroy$.next(); this.destroy$.complete(); }

  load(): void {
    this.loading = true;
    const appliesTo = this.filterApplies || undefined;
    this.svc.listCharges(this.page, this.pageSize, appliesTo).subscribe({
      next: p => {
        this.charges  = p.content;
        this.filtered = p.content;
        this.total     = p.totalElements;
        this.totalPages = p.totalPages;
        this.loading   = false;
      },
      error: () => { this.loading = false; },
    });
  }

  onSearch(q: string): void { this.searchQuery = q; this.search$.next(q); }
  onFilterChange(): void    { this.page = 0; this.load(); }
  prevPage(): void { if (this.page > 0) { this.page--; this.load(); } }
  nextPage(): void { if (this.page < this.totalPages - 1) { this.page++; this.load(); } }

  get startRow(): number { return this.total === 0 ? 0 : this.page * this.pageSize + 1; }
  get endRow(): number   { return Math.min((this.page + 1) * this.pageSize, this.total); }

  // Modal — create
  openCreate(): void {
    this.editTarget = null;
    this.form = BLANK_FORM();
    this.saveError = '';
    this.showModal = true;
  }

  // Modal — edit
  openEdit(c: ChargeDefinition, event: Event): void {
    event.stopPropagation();
    this.editTarget = c;
    this.form = {
      name: c.name,
      currencyCode: c.currencyCode,
      chargeAppliesTo: c.chargeAppliesTo,
      chargeTimeType: c.chargeTimeType,
      chargeCalculation: c.chargeCalculation,
      amount: c.amount,
      penalty: c.penalty,
      active: c.active,
    };
    this.saveError = '';
    this.showModal = true;
  }

  closeModal(): void { this.showModal = false; }

  onAppliesToChange(): void {
    // Reset time type when applies-to changes (options list changes)
    this.form.chargeTimeType = '';
  }

  save(): void {
    if (!this.form.chargeAppliesTo || !this.form.chargeTimeType ||
        !this.form.chargeCalculation || this.form.amount === null || !this.form.name.trim()) {
      this.saveError = 'All fields are required.';
      return;
    }
    this.saving = true;
    this.saveError = '';
    const body: ChargeCreateRequest = {
      name:              this.form.name.trim(),
      currencyCode:      this.form.currencyCode || 'USD',
      chargeAppliesTo:   this.form.chargeAppliesTo as ChargeAppliesTo,
      chargeTimeType:    this.form.chargeTimeType as ChargeTimeType,
      chargeCalculation: this.form.chargeCalculation as ChargeCalculation,
      amount:            this.form.amount!,
      penalty:           this.form.penalty,
      active:            this.form.active,
    };
    const op = this.editTarget
      ? this.svc.updateCharge(this.editTarget.id, body)
      : this.svc.createCharge(body);

    op.subscribe({
      next: () => { this.saving = false; this.showModal = false; this.load(); },
      error: () => { this.saving = false; this.saveError = 'Save failed. Please try again.'; },
    });
  }

  // Delete
  openDelete(c: ChargeDefinition, event: Event): void {
    event.stopPropagation();
    this.deleteTarget = c;
    this.showDeleteModal = true;
  }

  closeDelete(): void { this.showDeleteModal = false; this.deleteTarget = null; }

  confirmDelete(): void {
    if (!this.deleteTarget) return;
    this.svc.deleteCharge(this.deleteTarget.id).subscribe({
      next: () => { this.closeDelete(); this.load(); },
      error: () => { this.closeDelete(); },
    });
  }

  // Labels
  appliesToLabel(t: string): string {
    const map: Record<string, string> = {
      LOAN: 'Loan', SAVINGS: 'Savings', CLIENT: 'Client', SHARE: 'Share',
    };
    return map[t] ?? t;
  }

  timeTypeLabel(t: string): string {
    const map: Record<string, string> = {
      DISBURSEMENT: 'Disbursement', SPECIFIED_DUE_DATE: 'Specified Due Date',
      INSTALLMENT_FEE: 'Installment Fee', OVERDUE_INSTALLMENT: 'Overdue Installment',
      ANNUAL_FEE: 'Annual Fee', MONTHLY_FEE: 'Monthly Fee',
      WITHDRAWAL_FEE: 'Withdrawal Fee', SAVINGS_ACTIVATION: 'Savings Activation',
      SHARE_PURCHASE: 'Share Purchase',
    };
    return map[t] ?? t;
  }

  calculationLabel(t: string): string {
    const map: Record<string, string> = {
      FLAT: 'Flat', PERCENT_OF_AMOUNT: '% of Amount',
      PERCENT_OF_INTEREST: '% of Interest',
      PERCENT_OF_AMOUNT_AND_INTEREST: '% of Amount + Interest',
    };
    return map[t] ?? t;
  }

  amountDisplay(c: ChargeDefinition): string {
    if (c.chargeCalculation === 'FLAT') {
      return `${Number(c.amount).toLocaleString('en-US', { minimumFractionDigits: 2 })} ${c.currencyCode}`;
    }
    return `${Number(c.amount).toFixed(2)}%`;
  }
}
