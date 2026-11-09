import { Component, inject, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { StatusBadgeComponent } from '../../../../shared/components/status-badge/status-badge';
import { PaymentService, Payment, ExternalOutcome } from '../payment.service';

@Component({
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-payment-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule, StatusBadgeComponent],
  templateUrl: './payment-detail.html',
  styleUrl: './payment-detail.scss',
})
export class PaymentDetailComponent implements OnInit {
  private readonly route      = inject(ActivatedRoute);
  private readonly svc        = inject(PaymentService);

  payment: Payment | null = null;
  loading = true;
  error   = '';

  // Reverse modal
  showReverseModal = false;
  reverseReason    = '';
  reverseWorking   = false;
  reverseError     = '';

  // External payment outcome (ops) — backlog 1
  showOutcomeModal = false;
  outcome: ExternalOutcome = 'SETTLED';
  outcomeReason    = '';
  outcomeWorking   = false;
  outcomeError     = '';

  readonly typeLabels: Record<string, string> = {
    INTERNAL_TRANSFER: 'Internal Transfer',
    EXTERNAL_PAYMENT:  'External Payment',
    STANDING_ORDER:    'Standing Order',
    BILL_PAYMENT:      'Bill Payment',
  };
  readonly typeIcons: Record<string, string> = {
    INTERNAL_TRANSFER: 'swap_horiz',
    EXTERNAL_PAYMENT:  'public',
    STANDING_ORDER:    'repeat',
    BILL_PAYMENT:      'receipt',
  };

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id')!;
    this.svc.get(id).subscribe({
      next:  p  => { this.payment = p; this.loading = false; },
      error: () => { this.error = 'Payment not found.'; this.loading = false; },
    });
  }

  // ── Reverse ────────────────────────────────────────────────────────────────

  openReverseModal(): void {
    this.showReverseModal = true;
    this.reverseReason    = '';
    this.reverseError     = '';
    this.reverseWorking   = false;
  }

  closeReverseModal(): void { if (!this.reverseWorking) this.showReverseModal = false; }

  submitReverse(): void {
    if (!this.payment || !this.reverseReason.trim()) return;
    this.reverseWorking = true;
    this.reverseError   = '';
    this.svc.reverse(this.payment.id, this.reverseReason.trim()).subscribe({
      next: p => {
        this.payment        = p;
        this.reverseWorking = false;
        this.showReverseModal = false;
      },
      error: () => {
        this.reverseError   = 'Reversal failed. Only COMPLETED payments can be reversed.';
        this.reverseWorking = false;
      },
    });
  }

  // ── External payment outcome (ops) ──────────────────────────────────────────

  openOutcomeModal(outcome: ExternalOutcome): void {
    this.outcome          = outcome;
    this.outcomeReason    = '';
    this.outcomeError     = '';
    this.outcomeWorking   = false;
    this.showOutcomeModal = true;
  }

  closeOutcomeModal(): void { if (!this.outcomeWorking) this.showOutcomeModal = false; }

  submitOutcome(): void {
    if (!this.payment || !this.outcomeReason.trim()) return;
    this.outcomeWorking = true;
    this.outcomeError   = '';
    this.svc.recordExternalOutcome(this.payment.id, this.outcome, this.outcomeReason.trim()).subscribe({
      next: p => {
        this.payment          = p;
        this.outcomeWorking   = false;
        this.showOutcomeModal = false;
      },
      error: err => {
        const code = err?.error?.errors?.[0]?.code;
        this.outcomeError = code === 'SAME_USER'
          ? 'The outcome must be recorded by someone other than the user who sent the payment.'
          : (err?.error?.errors?.[0]?.message ?? 'Could not record the outcome.');
        this.outcomeWorking = false;
      },
    });
  }

  get isExternal(): boolean {
    return this.payment?.paymentType === 'EXTERNAL_PAYMENT';
  }

  /** A PROCESSING external payment waits for the gateway's callback, or for ops. */
  get awaitingSettlement(): boolean {
    return this.isExternal && this.payment?.status === 'PROCESSING';
  }

  // ── Display helpers ────────────────────────────────────────────────────────

  statusVariant(s: string): 'success' | 'warning' | 'error' | 'neutral' {
    if (s === 'COMPLETED') return 'success';
    if (s === 'PENDING' || s === 'PROCESSING') return 'warning';
    if (s === 'FAILED') return 'error';
    return 'neutral';
  }

  /** External payments aren't reversed: a return comes from the beneficiary's bank. */
  get canReverse(): boolean {
    return this.payment?.status === 'COMPLETED' && !this.isExternal;
  }
}
