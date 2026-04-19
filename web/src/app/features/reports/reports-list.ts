import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ReportService, Report, ReportParameter } from './report.service';

type ModalType = 'run' | 'create' | 'delete' | null;

interface ParamFormEntry {
  param:  ReportParameter;
  value:  string;
}

@Component({
  selector: 'app-reports-list',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './reports-list.html',
  styleUrl: './reports-list.scss',
})
export class ReportsListComponent implements OnInit {
  private readonly svc = inject(ReportService);

  reports:  Report[] = [];
  filtered: Report[] = [];
  loading   = true;
  error     = '';

  searchQuery    = '';
  categoryFilter = '';

  get categories(): string[] {
    const cats = new Set(this.reports.map(r => r.reportCategory ?? '').filter(Boolean));
    return Array.from(cats).sort();
  }

  // ── Run modal ──────────────────────────────────────────────────────────────
  activeModal:   ModalType = null;
  selectedReport: Report | null = null;
  paramEntries:  ParamFormEntry[] = [];

  running    = false;
  runError   = '';
  results:   Record<string, unknown>[] = [];
  resultCols: string[] = [];
  hasRun     = false;

  // ── Create modal ───────────────────────────────────────────────────────────
  createWorking = false;
  createError   = '';
  formName      = '';
  formType      = 'Table';
  formCategory  = '';
  formDesc      = '';
  formSql       = '';

  // ── Delete modal ───────────────────────────────────────────────────────────
  deletingId    = '';
  deletingName  = '';
  deleteWorking = false;
  deleteError   = '';

  ngOnInit(): void {
    this.loadReports();
  }

  loadReports(): void {
    this.svc.listReports().subscribe({
      next: list => { this.reports = list; this.applyFilter(); this.loading = false; },
      error: () => { this.error = 'Failed to load reports.'; this.loading = false; },
    });
  }

  applyFilter(): void {
    let r = this.reports;
    if (this.categoryFilter) r = r.filter(x => (x.reportCategory ?? '') === this.categoryFilter);
    if (this.searchQuery) {
      const q = this.searchQuery.toLowerCase();
      r = r.filter(x => x.reportName.toLowerCase().includes(q) ||
                         (x.description ?? '').toLowerCase().includes(q));
    }
    this.filtered = r;
  }

  // ── Run modal ──────────────────────────────────────────────────────────────

  openRunModal(r: Report): void {
    this.activeModal     = 'run';
    this.selectedReport  = r;
    this.paramEntries    = (r.reportParameters ?? []).map(p => ({
      param: p,
      value: p.defaultValue ?? '',
    }));
    this.running   = false;
    this.runError  = '';
    this.results   = [];
    this.resultCols = [];
    this.hasRun    = false;
  }

  runReport(): void {
    if (!this.selectedReport) return;
    // Validate required params
    const missing = this.paramEntries.filter(e => !e.param.optional && !e.value.trim());
    if (missing.length > 0) {
      this.runError = `Required: ${missing.map(e => e.param.parameterLabel ?? e.param.parameterName).join(', ')}`;
      return;
    }
    this.running  = true;
    this.runError = '';
    const params: Record<string, string> = {};
    for (const e of this.paramEntries) {
      if (e.value.trim()) params[e.param.parameterName] = e.value.trim();
    }
    this.svc.runReport(this.selectedReport.reportName, params).subscribe({
      next: rows => {
        this.results    = rows;
        this.resultCols = rows.length > 0 ? Object.keys(rows[0]) : [];
        this.running    = false;
        this.hasRun     = true;
      },
      error: () => { this.runError = 'Report execution failed. Check parameters.'; this.running = false; },
    });
  }

  exportFormat: 'csv' | 'xlsx' | 'pdf' = 'csv';

  exportReport(): void {
    if (!this.selectedReport) return;
    const params: Record<string, string> = {};
    for (const e of this.paramEntries) {
      if (e.value.trim()) params[e.param.parameterName] = e.value.trim();
    }
    const url = this.svc.getExportUrl(this.selectedReport.reportName, this.exportFormat, params);
    window.open(url, '_blank');
  }

  closeRunModal(): void {
    if (!this.running) { this.activeModal = null; this.selectedReport = null; }
  }

  // ── Create modal ───────────────────────────────────────────────────────────

  openCreateModal(): void {
    this.activeModal   = 'create';
    this.formName      = '';
    this.formType      = 'Table';
    this.formCategory  = '';
    this.formDesc      = '';
    this.formSql       = '';
    this.createWorking = false;
    this.createError   = '';
  }

  submitCreate(): void {
    if (!this.formName || !this.formSql) return;
    this.createWorking = true;
    this.svc.createReport({
      reportName:     this.formName,
      reportType:     this.formType,
      reportCategory: this.formCategory || undefined,
      description:    this.formDesc     || undefined,
      reportSql:      this.formSql,
    }).subscribe({
      next: r => {
        this.reports = [...this.reports, r];
        this.applyFilter();
        this.createWorking = false;
        this.activeModal   = null;
      },
      error: () => { this.createError = 'Failed to create report.'; this.createWorking = false; },
    });
  }

  closeCreateModal(): void { if (!this.createWorking) this.activeModal = null; }

  // ── Delete modal ───────────────────────────────────────────────────────────

  openDeleteModal(r: Report): void {
    this.activeModal   = 'delete';
    this.deletingId    = r.id;
    this.deletingName  = r.reportName;
    this.deleteWorking = false;
    this.deleteError   = '';
  }

  submitDelete(): void {
    this.deleteWorking = true;
    this.svc.deleteReport(this.deletingId).subscribe({
      next: () => {
        this.reports = this.reports.filter(r => r.id !== this.deletingId);
        this.applyFilter();
        this.deleteWorking = false;
        this.activeModal   = null;
      },
      error: () => { this.deleteError = 'Cannot delete a core system report.'; this.deleteWorking = false; },
    });
  }

  closeDeleteModal(): void { if (!this.deleteWorking) this.activeModal = null; }

  // ── Helpers ────────────────────────────────────────────────────────────────

  cellValue(row: Record<string, unknown>, col: string): string {
    const v = row[col];
    return v === null || v === undefined ? '—' : String(v);
  }

  paramInputType(p: ReportParameter): string {
    if (p.parameterType === 'DATE')    return 'date';
    if (p.parameterType === 'NUMBER')  return 'number';
    return 'text';
  }
}
