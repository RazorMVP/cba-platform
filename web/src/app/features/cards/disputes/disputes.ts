import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CardsService, CardDispute, DisputeStatus, DisputeReason, RaiseDisputeRequest, ResolveDisputeRequest, ChargebackReasonCode } from '../cards.service';
import { StatusBadgeComponent } from '../../../shared/components/status-badge/status-badge';

type V = 'success' | 'warning' | 'error' | 'info' | 'neutral' | 'primary';

@Component({
  selector: 'app-disputes',
  standalone: true,
  imports: [CommonModule, FormsModule, StatusBadgeComponent],
  templateUrl: './disputes.html',
  styleUrl: './disputes.scss',
})
export class DisputesComponent implements OnInit {
  private readonly svc = inject(CardsService);

  disputes: CardDispute[] = [];
  reasonCodes: ChargebackReasonCode[] = [];
  loading = true;
  statusFilter: DisputeStatus | '' = '';
  selectedDispute: CardDispute | null = null;

  showRaiseModal = false;
  raiseForm: RaiseDisputeRequest = { cardId: '', transactionRef: '', disputeReason: 'UNAUTHORIZED', raisedBy: '', originalAmount: 0 };

  showResolveModal = false;
  resolveForm: ResolveDisputeRequest = { resolvedBy: '', resolutionFavor: 'ISSUER', resolutionNotes: '' };

  readonly statuses: DisputeStatus[] = ['RAISED', 'RETRIEVAL_REQUESTED', 'CHARGEBACK_INITIATED', 'REPRESENTMENT', 'PRE_ARBITRATION', 'RESOLVED', 'WITHDRAWN'];
  readonly reasons: DisputeReason[] = ['UNAUTHORIZED', 'GOODS_NOT_RECEIVED', 'DUPLICATE', 'AMOUNT_MISMATCH', 'OTHER'];

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.svc.listDisputes(this.statusFilter || undefined).subscribe({ next: d => { this.disputes = d; this.loading = false; }, error: () => { this.loading = false; } });
    this.svc.listReasonCodes().subscribe({ next: rc => this.reasonCodes = rc, error: () => {} });
  }

  select(d: CardDispute): void { this.selectedDispute = d; }
  closeDetail(): void { this.selectedDispute = null; }

  raiseDispute(): void {
    this.svc.raiseDispute(this.raiseForm).subscribe({ next: () => { this.showRaiseModal = false; this.load(); } });
  }

  openResolve(d: CardDispute): void { this.selectedDispute = d; this.resolveForm = { resolvedBy: '', resolutionFavor: 'ISSUER', resolutionNotes: '' }; this.showResolveModal = true; }
  submitResolve(): void {
    if (!this.selectedDispute) return;
    this.svc.resolveDispute(this.selectedDispute.id, this.resolveForm).subscribe({ next: () => { this.showResolveModal = false; this.load(); this.closeDetail(); } });
  }

  advanceDispute(d: CardDispute, command: string): void {
    this.svc.disputeCommand(d.id, command).subscribe({ next: updated => {
      const i = this.disputes.findIndex(x => x.id === d.id);
      if (i >= 0) this.disputes[i] = updated;
      if (this.selectedDispute?.id === d.id) this.selectedDispute = updated;
    }});
  }

  statusVariant(s: DisputeStatus): V {
    const m: Record<string, V> = { RAISED: 'warning', RETRIEVAL_REQUESTED: 'info', CHARGEBACK_INITIATED: 'warning', REPRESENTMENT: 'info', PRE_ARBITRATION: 'error', RESOLVED: 'success', WITHDRAWN: 'neutral' };
    return m[s] ?? 'neutral';
  }
}
