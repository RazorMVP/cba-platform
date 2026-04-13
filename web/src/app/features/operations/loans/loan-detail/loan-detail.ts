import { Component, inject, OnInit } from '@angular/core';
import { CommonModule, CurrencyPipe, DatePipe, PercentPipe } from '@angular/common';
import { RouterLink, ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { StatusBadgeComponent } from '../../../../shared/components/status-badge/status-badge';
import {
  LoanService, Loan, RepaymentInstallment,
  LoanCharge, Guarantor, Collateral, AuditEntry,
  LoanCreateRequest,
} from '../loan.service';

export type LoanTab = 'summary' | 'schedule' | 'charges' | 'collateral' | 'audit';

@Component({
  selector: 'app-loan-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule, StatusBadgeComponent, CurrencyPipe, DatePipe, PercentPipe],
  templateUrl: './loan-detail.html',
  styleUrl: './loan-detail.scss',
})
export class LoanDetailComponent implements OnInit {
  private readonly route  = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly svc    = inject(LoanService);

  loan: Loan | null = null;
  loading = true;
  error = '';
  activeTab: LoanTab = 'summary';

  // Creation mode
  isNew = false;
  saving = false;
  saveError = '';
  newForm: LoanCreateRequest = { customerId: '', productId: '', principalAmount: 0, termMonths: 12 };

  // Schedule tab
  schedule: RepaymentInstallment[] = [];
  scheduleLoaded = false;
  scheduleLoading = false;

  // Charges tab
  charges: LoanCharge[] = [];
  chargesLoaded = false;
  chargesLoading = false;

  // Collateral tab
  guarantors: Guarantor[] = [];
  collateral: Collateral[] = [];
  collateralLoaded = false;
  collateralLoading = false;

  // Audit tab
  auditLog: AuditEntry[] = [];
  auditLoaded = false;
  auditLoading = false;

  // Repayment modal
  showRepaymentModal = false;
  repaymentAmount: number | null = null;
  repaymentDate = new Date().toISOString().slice(0, 10);
  repaymentSaving = false;
  repaymentError = '';

  // Reject modal
  showRejectModal = false;
  rejectReason = '';
  rejectSaving = false;

  // Approve modal
  showApproveModal = false;
  approveAmount: number | null = null;
  approveSaving = false;

  readonly tabs: Array<{ id: LoanTab; label: string; icon: string }> = [
    { id: 'summary',    label: 'Summary',    icon: 'summarize' },
    { id: 'schedule',   label: 'Repayment Schedule', icon: 'event_note' },
    { id: 'charges',    label: 'Charges',    icon: 'receipt' },
    { id: 'collateral', label: 'Guarantors & Collateral', icon: 'security' },
    { id: 'audit',      label: 'Audit Trail', icon: 'history' },
  ];

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id')!;
    if (id === 'new') {
      this.isNew = true;
      this.loading = false;
      return;
    }
    this.svc.get(id).subscribe({
      next:  l  => { this.loan = l; this.loading = false; },
      error: () => { this.error = 'Loan not found.'; this.loading = false; },
    });
  }

  submitCreate(): void {
    if (!this.newForm.customerId || !this.newForm.productId || this.newForm.principalAmount <= 0 || this.newForm.termMonths <= 0) return;
    this.saving = true;
    this.saveError = '';
    this.svc.create(this.newForm).subscribe({
      next:  l  => this.router.navigate(['..', l.id], { relativeTo: this.route }),
      error: () => { this.saveError = 'Failed to create loan. Please try again.'; this.saving = false; },
    });
  }

  selectTab(tab: LoanTab): void {
    this.activeTab = tab;
    const id = this.loan?.id;
    if (!id) return;

    if (tab === 'schedule' && !this.scheduleLoaded) {
      this.scheduleLoading = true;
      this.svc.getSchedule(id).subscribe({
        next:  s  => { this.schedule = s; this.scheduleLoaded = true; this.scheduleLoading = false; },
        error: () => { this.scheduleLoading = false; },
      });
    }
    if (tab === 'charges' && !this.chargesLoaded) {
      this.chargesLoading = true;
      this.svc.getCharges(id).subscribe({
        next:  c  => { this.charges = c; this.chargesLoaded = true; this.chargesLoading = false; },
        error: () => { this.chargesLoading = false; },
      });
    }
    if (tab === 'collateral' && !this.collateralLoaded) {
      this.collateralLoading = true;
      this.svc.getGuarantors(id).subscribe({ next: g => { this.guarantors = g; } });
      this.svc.getCollateral(id).subscribe({
        next:  c  => { this.collateral = c; this.collateralLoaded = true; this.collateralLoading = false; },
        error: () => { this.collateralLoading = false; },
      });
    }
    if (tab === 'audit' && !this.auditLoaded) {
      this.auditLoading = true;
      this.svc.getAuditLog(id).subscribe({
        next:  a  => { this.auditLog = a; this.auditLoaded = true; this.auditLoading = false; },
        error: () => { this.auditLoading = false; },
      });
    }
  }

  // ── Actions ──────────────────────────────────────────────

  submitApprove(): void {
    if (!this.loan) return;
    this.approveSaving = true;
    this.svc.approve(this.loan.id, this.approveAmount ?? undefined).subscribe({
      next:  l  => { this.loan = l; this.showApproveModal = false; this.approveSaving = false; },
      error: () => { this.approveSaving = false; },
    });
  }

  submitDisburse(): void {
    if (!this.loan) return;
    this.svc.disburse(this.loan.id).subscribe({ next: l => { this.loan = l; } });
  }

  submitReject(): void {
    if (!this.loan || !this.rejectReason.trim()) return;
    this.rejectSaving = true;
    this.svc.reject(this.loan.id, this.rejectReason).subscribe({
      next:  l  => { this.loan = l; this.showRejectModal = false; this.rejectReason = ''; this.rejectSaving = false; },
      error: () => { this.rejectSaving = false; },
    });
  }

  submitRepayment(): void {
    if (!this.loan || !this.repaymentAmount) return;
    this.repaymentSaving = true;
    this.repaymentError = '';
    this.svc.recordRepayment(this.loan.id, this.repaymentAmount, this.repaymentDate).subscribe({
      next: l => {
        this.loan = l;
        this.showRepaymentModal = false;
        this.repaymentAmount = null;
        this.repaymentSaving = false;
        // Invalidate schedule cache so next visit re-fetches
        this.scheduleLoaded = false;
        this.schedule = [];
      },
      error: () => { this.repaymentError = 'Repayment failed. Please check the amount and try again.'; this.repaymentSaving = false; },
    });
  }

  payCharge(charge: LoanCharge): void {
    if (!this.loan) return;
    this.svc.payCharge(this.loan.id, charge.id).subscribe({
      next: updated => {
        const idx = this.charges.findIndex(c => c.id === updated.id);
        if (idx >= 0) this.charges[idx] = updated;
      },
    });
  }

  // ── Helpers ──────────────────────────────────────────────

  get repaidPct(): number {
    if (!this.loan || !this.loan.principalAmount) return 0;
    return Math.round((1 - this.loan.outstandingBalance / this.loan.principalAmount) * 100);
  }

  get canApprove():  boolean { return !!this.loan && ['SUBMITTED','UNDER_REVIEW'].includes(this.loan.status); }
  get canDisburse(): boolean { return !!this.loan && this.loan.status === 'APPROVED'; }
  get canReject():   boolean { return !!this.loan && ['SUBMITTED','UNDER_REVIEW','APPROVED'].includes(this.loan.status); }
  get canRepay():    boolean { return !!this.loan && ['ACTIVE','IN_ARREARS'].includes(this.loan.status); }

  statusVariant(s: Loan['status']): 'success'|'warning'|'error'|'info'|'neutral'|'primary' {
    const m: Record<string,'success'|'warning'|'error'|'info'|'neutral'|'primary'> = {
      ACTIVE:'primary', SUBMITTED:'info', UNDER_REVIEW:'warning',
      APPROVED:'success', DISBURSED:'success', IN_ARREARS:'error',
      WRITTEN_OFF:'error', CLOSED_OBLIGATIONS_MET:'neutral',
    };
    return m[s] ?? 'neutral';
  }
  statusLabel(s: Loan['status']): string {
    const m: Record<string,string> = {
      SUBMITTED:'Submitted', UNDER_REVIEW:'Under Review', APPROVED:'Approved',
      DISBURSED:'Disbursed', ACTIVE:'Active', IN_ARREARS:'In Arrears',
      CLOSED_OBLIGATIONS_MET:'Closed', WRITTEN_OFF:'Written Off',
    };
    return m[s] ?? s;
  }

  installmentVariant(s: RepaymentInstallment['status']): 'success'|'warning'|'error'|'neutral' {
    return s === 'PAID' ? 'success' : s === 'OVERDUE' ? 'error' : s === 'PARTIAL' ? 'warning' : 'neutral';
  }

  chargeVariant(c: LoanCharge): 'success'|'warning'|'neutral' {
    return c.amountOutstanding === 0 ? 'success' : c.amountOutstanding < c.amount ? 'warning' : 'neutral';
  }
  chargeLabel(c: LoanCharge): string {
    return c.amountOutstanding === 0 ? 'Paid' : c.amountOutstanding < c.amount ? 'Partial' : 'Outstanding';
  }

  // Schedule totals
  sum(field: keyof RepaymentInstallment): number {
    return this.schedule.reduce((acc, inst) => acc + (Number(inst[field]) || 0), 0);
  }
}
