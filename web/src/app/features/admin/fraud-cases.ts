import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { AdminService, FraudCase } from './admin.service';

@Component({
  selector: 'app-fraud-cases',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './fraud-cases.html',
  styleUrl: './fraud-cases.scss',
})
export class FraudCasesComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();

  cases: FraudCase[] = [];
  total = 0;
  page = 0;
  pageSize = 20;
  loading = false;

  filterStatus = '';
  filterRisk = '';

  selected: FraudCase | null = null;
  panelOpen = false;

  showEditModal = false;
  showCreateModal = false;

  editStatus = '';
  editAssignedTo = '';
  editNotes = '';
  newTitle = '';
  newRisk = 'MEDIUM';
  newAssignedTo = '';

  readonly statusColors: Record<string, string> = {
    OPEN: 'badge-warning',
    UNDER_INVESTIGATION: 'badge-info',
    CLOSED: 'badge-success',
  };
  readonly riskColors: Record<string, string> = {
    LOW: 'badge-success', MEDIUM: 'badge-warning', HIGH: 'badge-error', CRITICAL: 'badge-error',
  };

  constructor(private svc: AdminService) {}

  ngOnInit() { this.loadCases(); }
  ngOnDestroy() { this.destroy$.next(); this.destroy$.complete(); }

  loadCases() {
    this.loading = true;
    this.svc.listFraudCases(this.filterStatus || undefined, this.filterRisk || undefined, this.page, this.pageSize)
      .pipe(takeUntil(this.destroy$))
      .subscribe({ next: r => { this.cases = r.content; this.total = r.totalElements; this.loading = false; },
                   error: () => { this.loading = false; } });
  }

  applyFilters() { this.page = 0; this.loadCases(); }
  clearFilters() { this.filterStatus = ''; this.filterRisk = ''; this.applyFilters(); }
  prevPage() { if (this.page > 0) { this.page--; this.loadCases(); } }
  nextPage() { if ((this.page + 1) * this.pageSize < this.total) { this.page++; this.loadCases(); } }

  openPanel(c: FraudCase) { this.selected = c; this.panelOpen = true; }
  closePanel() { this.panelOpen = false; this.selected = null; }

  openEdit() {
    if (!this.selected) return;
    this.editStatus = this.selected.status;
    this.editAssignedTo = this.selected.assignedTo ?? '';
    this.editNotes = this.selected.resolutionNotes ?? '';
    this.showEditModal = true;
  }
  confirmEdit() {
    if (!this.selected) return;
    this.svc.updateFraudCase(this.selected.id, this.editStatus, this.editAssignedTo, this.editNotes)
      .subscribe({ next: () => { this.showEditModal = false; this.loadCases(); this.closePanel(); } });
  }

  openCreate() { this.newTitle = ''; this.newRisk = 'MEDIUM'; this.newAssignedTo = ''; this.showCreateModal = true; }
  confirmCreate() {
    if (!this.newTitle) return;
    this.svc.createFraudCase(this.newTitle, undefined, this.newRisk, this.newAssignedTo)
      .subscribe({ next: () => { this.showCreateModal = false; this.loadCases(); } });
  }

  statusClass(s: string) { return this.statusColors[s] ?? 'badge-neutral'; }
  riskClass(r: string) { return this.riskColors[r] ?? 'badge-neutral'; }
  totalPages() { return Math.ceil(this.total / this.pageSize); }
}
