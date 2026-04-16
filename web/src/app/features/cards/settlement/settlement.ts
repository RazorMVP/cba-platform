import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CardsService, SettlementBatch, SettlementTransmission, BatchStatus } from '../cards.service';
import { StatusBadgeComponent } from '../../../shared/components/status-badge/status-badge';

type V = 'success' | 'warning' | 'error' | 'info' | 'neutral' | 'primary';

@Component({
  selector: 'app-settlement',
  standalone: true,
  imports: [CommonModule, StatusBadgeComponent],
  templateUrl: './settlement.html',
  styleUrl: './settlement.scss',
})
export class SettlementComponent implements OnInit {
  private readonly svc = inject(CardsService);

  batches: SettlementBatch[] = [];
  transmissions: SettlementTransmission[] = [];
  loading = true;
  activeTab: 'batches' | 'transmissions' = 'batches';
  expandedBatch: string | null = null;

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.svc.listBatches().subscribe({ next: b => { this.batches = b; this.loading = false; }, error: () => { this.loading = false; } });
    this.svc.listTransmissions().subscribe({ next: t => this.transmissions = t, error: () => {} });
  }

  closeBatch(id: string): void { this.svc.closeBatch(id).subscribe({ next: () => this.load() }); }
  triggerExport(batchId: string): void { this.svc.triggerExport(batchId).subscribe({ next: () => this.load() }); }
  toggleExpand(id: string): void { this.expandedBatch = this.expandedBatch === id ? null : id; }

  batchTransmissions(batchId: string): SettlementTransmission[] { return this.transmissions.filter(t => t.batchId === batchId); }

  statusVariant(s: BatchStatus | string): V {
    const m: Record<string, V> = { OPEN: 'info', CLOSED: 'warning', SETTLED: 'success', FAILED: 'error', TRANSMITTED: 'success', PENDING: 'info', ACKNOWLEDGED: 'success' };
    return m[s] ?? 'neutral';
  }

  totalAmount(batch: SettlementBatch): string { return (batch.totalAmount / 100).toFixed(2); }
}
