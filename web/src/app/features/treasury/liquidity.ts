import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import {
  TreasuryService,
  LiquidityPosition,
  CashFlowEntry,
  ReserveRequirement,
  ReserveRequest,
  LiquiditySnapshot,
} from './treasury.service';

type ActiveTab = 'position' | 'cashflow' | 'reserves' | 'history';
type ActiveModal = 'createReserve' | 'editReserve' | 'deleteReserve' | 'snapshot' | null;

@Component({
  selector: 'app-treasury-liquidity',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './liquidity.html',
  styleUrl: './liquidity.scss',
})
export class TreasuryLiquidityComponent implements OnInit, OnDestroy {
  private readonly destroy$ = new Subject<void>();

  activeTab: ActiveTab = 'position';
  activeModal: ActiveModal = null;

  // Position tab
  positions: LiquidityPosition[] = [];
  selectedCurrency = 'USD';
  loadingPositions = false;

  // Cash flow tab
  cashFlow: CashFlowEntry[] = [];
  forecastDays = 30;
  loadingCashFlow = false;

  // Reserves tab
  reserves: ReserveRequirement[] = [];
  loadingReserves = false;
  reserveForm: ReserveRequest = this.blankReserveForm();
  editingReserve: ReserveRequirement | null = null;
  deletingReserve: ReserveRequirement | null = null;
  savingReserve = false;

  // History tab
  snapshots: LiquiditySnapshot[] = [];
  snapshotCurrency = 'USD';
  loadingSnapshots = false;
  takingSnapshot = false;

  constructor(private svc: TreasuryService) {}

  ngOnInit(): void {
    this.loadPositions();
    this.loadReserves();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  // ── Tab ──────────────────────────────────────────────────────────────────────

  selectTab(tab: ActiveTab): void {
    this.activeTab = tab;
    if (tab === 'cashflow' && this.cashFlow.length === 0) this.loadCashFlow();
    if (tab === 'history' && this.snapshots.length === 0) this.loadSnapshots();
  }

  // ── Position ─────────────────────────────────────────────────────────────────

  loadPositions(): void {
    this.loadingPositions = true;
    this.svc.getLiquidityPositions().pipe(takeUntil(this.destroy$)).subscribe({
      next: (data) => { this.positions = data; this.loadingPositions = false; },
      error: () => { this.loadingPositions = false; },
    });
  }

  get selectedPosition(): LiquidityPosition | undefined {
    return this.positions.find(p => p.currency === this.selectedCurrency);
  }

  get currenciesAvailable(): string[] {
    return this.positions.map(p => p.currency);
  }

  alertClass(level: string): string {
    if (level === 'BREACH') return 'alert-breach';
    if (level === 'WARN')   return 'alert-warn';
    return 'alert-ok';
  }

  alertLabel(level: string): string {
    if (level === 'BREACH') return 'Below Reserve';
    if (level === 'WARN')   return 'Near Threshold';
    return 'Adequate';
  }

  // ── Cash Flow ─────────────────────────────────────────────────────────────────

  loadCashFlow(): void {
    this.loadingCashFlow = true;
    this.svc.getCashFlowForecast(this.selectedCurrency, this.forecastDays)
      .pipe(takeUntil(this.destroy$)).subscribe({
        next: (data) => { this.cashFlow = data; this.loadingCashFlow = false; },
        error: () => { this.loadingCashFlow = false; },
      });
  }

  cashFlowInflows(): number {
    return this.cashFlow.filter(e => e.direction === 'INFLOW').reduce((s, e) => s + e.amount, 0);
  }

  cashFlowOutflows(): number {
    return this.cashFlow.filter(e => e.direction === 'OUTFLOW').reduce((s, e) => s + e.amount, 0);
  }

  typeLabel(type: string): string {
    const map: Record<string, string> = {
      PLACEMENT_MATURITY:  'Placement Maturity',
      INTERBANK_REPAYMENT: 'Interbank Repayment',
      LOAN_REPAYMENT:      'Loan Repayment',
    };
    return map[type] ?? type;
  }

  // ── Reserves ─────────────────────────────────────────────────────────────────

  loadReserves(): void {
    this.loadingReserves = true;
    this.svc.listReserveRequirements().pipe(takeUntil(this.destroy$)).subscribe({
      next: (data) => { this.reserves = data; this.loadingReserves = false; },
      error: () => { this.loadingReserves = false; },
    });
  }

  openCreateReserve(): void {
    this.reserveForm = this.blankReserveForm();
    this.editingReserve = null;
    this.activeModal = 'createReserve';
  }

  openEditReserve(r: ReserveRequirement): void {
    this.editingReserve = r;
    this.reserveForm = {
      currencyCode:           r.currencyCode,
      minimumBalance:         r.minimumBalance,
      minimumRatioPercent:    r.minimumRatioPercent,
      alertThresholdPercent:  r.alertThresholdPercent,
      regulatoryReference:    r.regulatoryReference,
    };
    this.activeModal = 'editReserve';
  }

  openDeleteReserve(r: ReserveRequirement): void {
    this.deletingReserve = r;
    this.activeModal = 'deleteReserve';
  }

  saveReserve(): void {
    this.savingReserve = true;
    const obs = this.editingReserve
      ? this.svc.updateReserveRequirement(this.editingReserve.id, this.reserveForm)
      : this.svc.createReserveRequirement(this.reserveForm);
    obs.pipe(takeUntil(this.destroy$)).subscribe({
      next: () => { this.activeModal = null; this.savingReserve = false; this.loadReserves(); },
      error: () => { this.savingReserve = false; },
    });
  }

  confirmDeleteReserve(): void {
    if (!this.deletingReserve) return;
    this.svc.deleteReserveRequirement(this.deletingReserve.id)
      .pipe(takeUntil(this.destroy$)).subscribe({
        next: () => { this.activeModal = null; this.loadReserves(); },
      });
  }

  // ── Snapshots ─────────────────────────────────────────────────────────────────

  loadSnapshots(): void {
    this.loadingSnapshots = true;
    this.svc.getLiquiditySnapshots(this.snapshotCurrency)
      .pipe(takeUntil(this.destroy$)).subscribe({
        next: (data) => { this.snapshots = data; this.loadingSnapshots = false; },
        error: () => { this.loadingSnapshots = false; },
      });
  }

  triggerSnapshot(): void {
    this.takingSnapshot = true;
    this.svc.takeSnapshot().pipe(takeUntil(this.destroy$)).subscribe({
      next: () => {
        this.takingSnapshot = false;
        this.activeModal = null;
        if (this.activeTab === 'history') this.loadSnapshots();
      },
      error: () => { this.takingSnapshot = false; },
    });
  }

  // ── Shared ───────────────────────────────────────────────────────────────────

  closeModal(): void { this.activeModal = null; }

  fmt(n: number | undefined, ccy = 'USD'): string {
    if (n == null) return '—';
    return new Intl.NumberFormat('en-US', { style: 'currency', currency: ccy, maximumFractionDigits: 0 }).format(n);
  }

  pct(part: number, total: number): string {
    if (!total || total === 0) return '—';
    return (Math.abs(part / total) * 100).toFixed(1) + '%';
  }

  private blankReserveForm(): ReserveRequest {
    return { currencyCode: '', minimumBalance: 0 };
  }
}
