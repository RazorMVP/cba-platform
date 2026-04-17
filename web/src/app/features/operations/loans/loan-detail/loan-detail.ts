import { Component, inject, OnInit } from '@angular/core';
import { CommonModule, CurrencyPipe, DatePipe, PercentPipe } from '@angular/common';
import { RouterLink, ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { StatusBadgeComponent } from '../../../../shared/components/status-badge/status-badge';
import {
  LoanService, Loan, RepaymentInstallment,
  LoanCharge, AvailableCharge, Guarantor, Collateral, AuditEntry,
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

  // Add charge modal
  showAddChargeModal = false;
  availableCharges: AvailableCharge[] = [];
  availableChargesLoaded = false;
  addChargeDefinitionId = '';
  addChargeAmount: number | null = null;
  addChargeDueDate = '';
  addChargeSaving = false;
  addChargeError = '';

  // Waive charge confirm
  showWaiveChargeModal = false;
  waiveChargeTarget: LoanCharge | null = null;
  waiveSaving = false;
  waiveError = '';

  // Delete charge confirm
  showDeleteChargeModal = false;
  deleteChargeTarget: LoanCharge | null = null;

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

  // Write-off modal
  showWriteOffModal = false;
  writeOffReason = '';
  writeOffDate = new Date().toISOString().slice(0, 10);
  writeOffSaving = false;
  writeOffError = '';

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

  submitWriteOff(): void {
    if (!this.loan || !this.writeOffReason.trim()) return;
    this.writeOffSaving = true;
    this.writeOffError = '';
    this.svc.writeOff(this.loan.id, this.writeOffReason.trim(), this.writeOffDate || undefined).subscribe({
      next: l  => { this.loan = l; this.showWriteOffModal = false; this.writeOffReason = ''; this.writeOffSaving = false; },
      error: () => { this.writeOffError = 'Write-off failed. Please try again.'; this.writeOffSaving = false; },
    });
  }

  payCharge(charge: LoanCharge): void {
    if (!this.loan) return;
    this.svc.payCharge(this.loan.id, charge.id).subscribe({
      next: updated => this.replaceCharge(updated),
    });
  }

  openAddCharge(): void {
    this.addChargeDefinitionId = '';
    this.addChargeAmount = null;
    this.addChargeDueDate = '';
    this.addChargeError = '';
    this.showAddChargeModal = true;
    if (!this.availableChargesLoaded) {
      this.svc.listAvailableCharges().subscribe({
        next: list => { this.availableCharges = list; this.availableChargesLoaded = true; },
      });
    }
  }

  onAddChargeDefChange(): void {
    const def = this.availableCharges.find(c => c.id === this.addChargeDefinitionId);
    if (def) this.addChargeAmount = def.amount;
  }

  submitAddCharge(): void {
    if (!this.loan || !this.addChargeDefinitionId || !this.addChargeAmount) {
      this.addChargeError = 'Charge definition and amount are required.';
      return;
    }
    this.addChargeSaving = true;
    this.addChargeError = '';
    this.svc.addCharge(this.loan.id, this.addChargeDefinitionId,
                       this.addChargeAmount, this.addChargeDueDate || undefined).subscribe({
      next: c => {
        this.charges = [...this.charges, c];
        this.showAddChargeModal = false;
        this.addChargeSaving = false;
      },
      error: () => { this.addChargeError = 'Failed to add charge.'; this.addChargeSaving = false; },
    });
  }

  openWaiveCharge(c: LoanCharge): void {
    this.waiveChargeTarget = c;
    this.waiveError = '';
    this.showWaiveChargeModal = true;
  }

  confirmWaiveCharge(): void {
    if (!this.loan || !this.waiveChargeTarget || this.waiveSaving) return;
    this.waiveSaving = true;
    this.waiveError = '';
    this.svc.waiveCharge(this.loan.id, this.waiveChargeTarget.id).subscribe({
      next: updated => {
        this.replaceCharge(updated);
        this.showWaiveChargeModal = false;
        this.waiveChargeTarget = null;
        this.waiveSaving = false;
      },
      error: () => {
        this.waiveError = 'Failed to waive charge. Please try again.';
        this.waiveSaving = false;
      },
    });
  }

  openDeleteCharge(c: LoanCharge): void { this.deleteChargeTarget = c; this.showDeleteChargeModal = true; }

  confirmDeleteCharge(): void {
    if (!this.loan || !this.deleteChargeTarget) return;
    const id = this.deleteChargeTarget.id;
    this.svc.deleteCharge(this.loan.id, id).subscribe({
      next: () => {
        this.charges = this.charges.filter(c => c.id !== id);
        this.showDeleteChargeModal = false;
        this.deleteChargeTarget = null;
      },
    });
  }

  private replaceCharge(updated: LoanCharge): void {
    const idx = this.charges.findIndex(c => c.id === updated.id);
    if (idx >= 0) this.charges = [...this.charges.slice(0, idx), updated, ...this.charges.slice(idx + 1)];
  }

  // ── Helpers ──────────────────────────────────────────────

  get repaidPct(): number {
    if (!this.loan || !this.loan.principalAmount) return 0;
    return Math.round((1 - this.loan.outstandingBalance / this.loan.principalAmount) * 100);
  }

  get canApprove():   boolean { return !!this.loan && ['SUBMITTED','UNDER_REVIEW'].includes(this.loan.status); }
  get canDisburse():  boolean { return !!this.loan && this.loan.status === 'APPROVED'; }
  get canReject():    boolean { return !!this.loan && ['SUBMITTED','UNDER_REVIEW','APPROVED'].includes(this.loan.status); }
  get canRepay():     boolean { return !!this.loan && ['ACTIVE','IN_ARREARS'].includes(this.loan.status); }
  get canWriteOff():  boolean { return !!this.loan && ['ACTIVE','IN_ARREARS'].includes(this.loan.status); }

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
