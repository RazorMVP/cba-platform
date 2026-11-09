import { Component, OnInit, inject, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  CardsService, SettlementBatch, SettlementTransmission, BatchStatus,
  SchemeReportResult, SchemeSettlementReport, SchemeType,
} from '../cards.service';
import { StatusBadgeComponent } from '../../../shared/components/status-badge/status-badge';

type V = 'success' | 'warning' | 'error' | 'info' | 'neutral' | 'primary';
type Tab = 'batches' | 'transmissions' | 'reports';

/** Local yyyy-MM-dd of the day before today (a scheme report is for a date that's over). */
function yesterday(): string {
  const d = new Date();
  d.setDate(d.getDate() - 1);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

/** Plain-language text for a scheme report refusal. */
const REASONS: Record<string, string> = {
  DIFFERENCE_OVER_TOLERANCE: 'The difference between the reported and expected net is above the tolerance for this currency.',
  ALREADY_RECORDED: 'A report with another amount is already recorded for this scheme, date and currency.',
  REPORT_IN_PROGRESS: 'A report for this scheme, date and currency is still being posted with another amount.',
  DATE_NOT_CLOSED: 'The settlement date must be before today.',
  BATCH_NOT_CLOSED: 'A batch of that date is still open, or items are still clearing.',
  UNKNOWN_CURRENCY: 'Unknown currency.',
  INVALID_AMOUNT: 'The amount has more decimals than the currency allows.',
  BACKEND_UNAVAILABLE: 'The core banking system could not be reached. Record the same report again to finish it.',
};

@Component({
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-settlement',
  standalone: true,
  imports: [CommonModule, FormsModule, StatusBadgeComponent],
  templateUrl: './settlement.html',
  styleUrl: './settlement.scss',
})
export class SettlementComponent implements OnInit {
  private readonly svc = inject(CardsService);

  batches: SettlementBatch[] = [];
  transmissions: SettlementTransmission[] = [];
  loading = true;
  activeTab: Tab = 'batches';
  expandedBatch: string | null = null;
  exportError: string | null = null;

  // ── Scheme reports (backlog 15b/15d) ──
  readonly schemes: SchemeType[] = ['VISA', 'MASTERCARD', 'VERVE', 'AFRIGO', 'UNIONPAY'];
  reportForm: { scheme: SchemeType; settlementDate: string; currency: string; netAmount: number | null } =
    { scheme: 'VISA', settlementDate: yesterday(), currency: 'USD', netAmount: null };
  expected: SchemeReportResult | null = null;
  expectedError: string | null = null;
  recordResult: SchemeReportResult | null = null;
  recordError: string | null = null;
  recording = false;
  reportsDate = yesterday();
  reports: SchemeSettlementReport[] = [];
  reportsLoading = false;

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.svc.listBatches().subscribe({ next: b => { this.batches = b; this.loading = false; }, error: () => { this.loading = false; } });
    this.svc.listTransmissions().subscribe({ next: t => this.transmissions = t, error: () => {} });
  }

  closeBatch(id: string): void { this.svc.closeBatch(id).subscribe({ next: () => this.load() }); }
  // Card-service refuses an export with a reason (e.g. 409 PRESENTMENT_REQUIRES_ACQUIRER_ROLE for an issuer).
  triggerExport(batchId: string): void {
    this.exportError = null;
    this.svc.triggerExport(batchId).subscribe({
      next: () => this.load(),
      error: e => { this.exportError = e?.error?.errors?.[0]?.message ?? 'Export failed'; },
    });
  }

  setTab(tab: Tab): void {
    this.activeTab = tab;
    if (tab === 'reports') this.loadReports();
  }

  /** Expected net for the form's scheme, date and currency: what the scheme's report should show. */
  lookupExpected(): void {
    const f = this.reportForm;
    this.expectedError = null;
    this.svc.expectedSchemeNet(f.scheme, f.settlementDate, f.currency).subscribe({
      next: r => {
        this.expected = r;
        if (r.reason) this.expectedError = this.reasonText(r.reason);
      },
      error: e => { this.expected = null; this.expectedError = e?.error?.errors?.[0]?.message ?? 'Lookup failed'; },
    });
  }

  /** Records the scheme's reported net; the result (or refusal) shows expected, reported and the difference. */
  recordReport(): void {
    const f = this.reportForm;
    if (f.netAmount === null || f.netAmount === undefined || Number.isNaN(Number(f.netAmount))) {
      this.recordError = 'Enter the net amount from the scheme report.';
      return;
    }
    this.recordError = null;
    this.recordResult = null;
    this.recording = true;
    this.svc.recordSchemeReport({
      scheme: f.scheme, settlementDate: f.settlementDate, currency: f.currency, netAmount: Number(f.netAmount),
    }).subscribe({
      next: r => {
        this.recording = false;
        this.recordResult = r;
        this.reportsDate = f.settlementDate;
        this.loadReports();
      },
      error: e => {
        this.recording = false;
        this.recordError = e?.error?.errors?.[0]?.message ?? 'Recording failed';
      },
    });
  }

  loadReports(): void {
    this.reportsLoading = true;
    this.svc.listSchemeReports(this.reportsDate || undefined).subscribe({
      next: r => { this.reports = r; this.reportsLoading = false; },
      error: () => { this.reports = []; this.reportsLoading = false; },
    });
  }

  reasonText(reason: string | null): string {
    if (!reason) return '';
    return REASONS[reason] ?? reason;
  }

  /** A signed amount in major units, 2 decimals; '—' when absent. */
  amount(v: number | null | undefined): string {
    if (v === null || v === undefined) return '—';
    return Number(v).toFixed(2);
  }

  /** The difference with its sign, for display. */
  signedAmount(v: number | null | undefined): string {
    if (v === null || v === undefined) return '—';
    const n = Number(v);
    return (n > 0 ? '+' : '') + n.toFixed(2);
  }

  toggleExpand(id: string): void { this.expandedBatch = this.expandedBatch === id ? null : id; }

  batchTransmissions(batchId: string): SettlementTransmission[] { return this.transmissions.filter(t => t.batchId === batchId); }

  statusVariant(s: BatchStatus | string): V {
    const m: Record<string, V> = {
      OPEN: 'info', CLOSED: 'warning', SETTLED: 'success', FAILED: 'error', TRANSMITTED: 'success', PENDING: 'info',
      SENDING: 'info', ACKNOWLEDGED: 'success', POSTING: 'warning', RECORDED: 'success',
    };
    return m[s] ?? 'neutral';
  }

  totalAmount(batch: SettlementBatch): string { return (batch.totalAmount / 100).toFixed(2); }
}
