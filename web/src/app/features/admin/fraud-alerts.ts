import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { AdminService, FraudAlert, FraudCase } from './admin.service';

@Component({
  selector: 'app-fraud-alerts',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './fraud-alerts.html',
  styleUrl: './fraud-alerts.scss',
})
export class FraudAlertsComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();

  alerts: FraudAlert[] = [];
  cases: FraudCase[] = [];
  total = 0;
  page = 0;
  pageSize = 20;
  loading = false;

  filterStatus = '';
  filterSeverity = '';

  selected: FraudAlert | null = null;
  panelOpen = false;

  showReviewModal = false;
  showCloseModal = false;
  showLinkModal = false;
  showCreateCaseModal = false;

  closeStatus = 'CLOSED_FALSE_POSITIVE';
  reviewedBy = '';
  selectedCaseId = '';
  newCaseTitle = '';
  newCaseRisk = 'MEDIUM';

  readonly severityColors: Record<string, string> = {
    LOW: 'badge-info', MEDIUM: 'badge-warning', HIGH: 'badge-error', CRITICAL: 'badge-critical',
  };

  constructor(private svc: AdminService) {}

  ngOnInit() { this.loadAlerts(); this.loadCases(); }
  ngOnDestroy() { this.destroy$.next(); this.destroy$.complete(); }

  loadAlerts() {
    this.loading = true;
    this.svc.listFraudAlerts(this.filterStatus || undefined, this.filterSeverity || undefined, this.page, this.pageSize)
      .pipe(takeUntil(this.destroy$))
      .subscribe({ next: r => { this.alerts = r.content; this.total = r.totalElements; this.loading = false; },
                   error: () => { this.loading = false; } });
  }

  loadCases() {
    this.svc.listFraudCases(undefined, undefined, 0, 100)
      .pipe(takeUntil(this.destroy$))
      .subscribe({ next: r => { this.cases = r.content; }, error: () => {} });
  }

  applyFilters() { this.page = 0; this.loadAlerts(); }
  clearFilters() { this.filterStatus = ''; this.filterSeverity = ''; this.applyFilters(); }
  prevPage() { if (this.page > 0) { this.page--; this.loadAlerts(); } }
  nextPage() { if ((this.page + 1) * this.pageSize < this.total) { this.page++; this.loadAlerts(); } }

  openPanel(a: FraudAlert) { this.selected = a; this.panelOpen = true; }
  closePanel() { this.panelOpen = false; this.selected = null; }

  openReview() { this.reviewedBy = ''; this.showReviewModal = true; }
  confirmReview() {
    if (!this.selected) return;
    this.svc.reviewFraudAlert(this.selected.id, this.reviewedBy)
      .subscribe({ next: () => { this.showReviewModal = false; this.loadAlerts(); this.closePanel(); } });
  }

  openClose() { this.closeStatus = 'CLOSED_FALSE_POSITIVE'; this.reviewedBy = ''; this.showCloseModal = true; }
  confirmClose() {
    if (!this.selected) return;
    this.svc.closeFraudAlert(this.selected.id, this.closeStatus, this.reviewedBy)
      .subscribe({ next: () => { this.showCloseModal = false; this.loadAlerts(); this.closePanel(); } });
  }

  openLink() { this.selectedCaseId = ''; this.showLinkModal = true; }
  confirmLink() {
    if (!this.selected || !this.selectedCaseId) return;
    this.svc.linkAlertToCase(this.selected.id, this.selectedCaseId)
      .subscribe({ next: () => { this.showLinkModal = false; this.loadAlerts(); } });
  }

  openCreateCase() { this.newCaseTitle = ''; this.newCaseRisk = 'MEDIUM'; this.showCreateCaseModal = true; }
  confirmCreateCase() {
    if (!this.newCaseTitle) return;
    const customerId = this.selected?.customerId;
    this.svc.createFraudCase(this.newCaseTitle, customerId, this.newCaseRisk, '')
      .subscribe({ next: () => { this.showCreateCaseModal = false; this.loadCases(); } });
  }

  severityClass(s: string) { return this.severityColors[s] ?? 'badge-neutral'; }
  totalPages() { return Math.ceil(this.total / this.pageSize); }
}
