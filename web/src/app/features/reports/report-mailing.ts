import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ReportService, ReportMailingJob, ReportMailingRequest, MailingOutputType, Report } from './report.service';

type ModalType = 'create' | 'edit' | 'delete' | null;

const RRULE_PRESETS = [
  { label: 'Daily',        value: 'FREQ=DAILY' },
  { label: 'Weekly (Mon)', value: 'FREQ=WEEKLY;BYDAY=MO' },
  { label: 'Monthly (1st)',value: 'FREQ=MONTHLY;BYMONTHDAY=1' },
  { label: 'Custom…',      value: '' },
];

@Component({
  selector: 'app-report-mailing',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './report-mailing.html',
  styleUrl: './report-mailing.scss',
})
export class ReportMailingComponent implements OnInit {
  private readonly svc = inject(ReportService);

  mailingJobs: ReportMailingJob[] = [];
  reports:     Report[] = [];
  loading   = true;
  error     = '';

  // ── Modal ──────────────────────────────────────────────────────────────────
  activeModal:   ModalType = null;
  editingId      = '';
  deletingName   = '';
  modalWorking   = false;
  modalError     = '';

  readonly outputTypes: MailingOutputType[] = ['CSV', 'PDF', 'XLS'];
  readonly rrulePresets = RRULE_PRESETS;

  // Form fields
  formName        = '';
  formReportName  = '';
  formRecipients  = '';
  formSubject     = '';
  formMessage     = '';
  formRecurrence  = 'FREQ=DAILY';
  formCustomRrule = '';
  formOutputType: MailingOutputType = 'CSV';
  formActive      = true;
  rrulePreset     = 'FREQ=DAILY';

  // Per-job run state
  runningIds = new Set<string>();
  runErrors  = new Map<string, string>();

  ngOnInit(): void {
    this.loadMailingJobs();
    this.svc.listReports().subscribe({
      next: list => { this.reports = list; },
    });
  }

  loadMailingJobs(): void {
    this.svc.listMailingJobs().subscribe({
      next: list => { this.mailingJobs = list; this.loading = false; },
      error: () => { this.error = 'Failed to load mailing jobs.'; this.loading = false; },
    });
  }

  // ── Run now ────────────────────────────────────────────────────────────────

  runNow(job: ReportMailingJob): void {
    this.runningIds.add(job.id);
    this.runErrors.delete(job.id);
    this.svc.runMailingJob(job.id).subscribe({
      next: () => {
        this.runningIds.delete(job.id);
        this.loadMailingJobs();
      },
      error: () => {
        this.runErrors.set(job.id, 'Failed to trigger mailing job.');
        this.runningIds.delete(job.id);
      },
    });
  }

  isRunning(job: ReportMailingJob): boolean { return this.runningIds.has(job.id); }
  runError(job: ReportMailingJob): string   { return this.runErrors.get(job.id) ?? ''; }

  // ── Modals ─────────────────────────────────────────────────────────────────

  openCreateModal(): void {
    this.activeModal    = 'create';
    this.editingId      = '';
    this.formName       = '';
    this.formReportName = this.reports[0]?.reportName ?? '';
    this.formRecipients = '';
    this.formSubject    = '';
    this.formMessage    = '';
    this.rrulePreset    = 'FREQ=DAILY';
    this.formRecurrence = 'FREQ=DAILY';
    this.formOutputType = 'CSV';
    this.formActive     = true;
    this.modalWorking   = false;
    this.modalError     = '';
  }

  openEditModal(job: ReportMailingJob): void {
    this.activeModal    = 'edit';
    this.editingId      = job.id;
    this.formName       = job.name;
    this.formReportName = job.reportName;
    this.formRecipients = job.emailRecipients;
    this.formSubject    = job.emailSubject;
    this.formMessage    = job.emailMessage ?? '';
    this.formOutputType = job.outputType;
    this.formActive     = job.isActive;
    // Try to match a preset
    const preset = RRULE_PRESETS.find(p => p.value === job.recurrence);
    this.rrulePreset    = preset ? preset.value : '';
    this.formRecurrence = job.recurrence;
    this.modalWorking   = false;
    this.modalError     = '';
  }

  openDeleteModal(job: ReportMailingJob): void {
    this.activeModal   = 'delete';
    this.editingId     = job.id;
    this.deletingName  = job.name;
    this.modalWorking  = false;
    this.modalError    = '';
  }

  onRrulePresetChange(): void {
    if (this.rrulePreset) this.formRecurrence = this.rrulePreset;
  }

  get effectiveRrule(): string {
    return this.rrulePreset ? this.rrulePreset : this.formRecurrence;
  }

  submitModal(): void {
    if (!this.formName || !this.formReportName || !this.formRecipients || !this.formSubject) return;
    this.modalWorking = true;
    const req: ReportMailingRequest = {
      name:            this.formName,
      reportName:      this.formReportName,
      emailRecipients: this.formRecipients,
      emailSubject:    this.formSubject,
      emailMessage:    this.formMessage || undefined,
      recurrence:      this.effectiveRrule || 'FREQ=DAILY',
      outputType:      this.formOutputType,
      isActive:        this.formActive,
    };
    const call = this.activeModal === 'create'
      ? this.svc.createMailingJob(req)
      : this.svc.updateMailingJob(this.editingId, req);

    call.subscribe({
      next: j => {
        if (this.activeModal === 'create') {
          this.mailingJobs = [...this.mailingJobs, j];
        } else {
          this.mailingJobs = this.mailingJobs.map(x => x.id === j.id ? j : x);
        }
        this.modalWorking = false;
        this.activeModal  = null;
      },
      error: () => { this.modalError = 'Failed to save mailing job.'; this.modalWorking = false; },
    });
  }

  submitDelete(): void {
    this.modalWorking = true;
    this.svc.deleteMailingJob(this.editingId).subscribe({
      next: () => {
        this.mailingJobs  = this.mailingJobs.filter(j => j.id !== this.editingId);
        this.modalWorking = false;
        this.activeModal  = null;
      },
      error: () => { this.modalError = 'Failed to delete mailing job.'; this.modalWorking = false; },
    });
  }

  closeModal(): void { if (!this.modalWorking) this.activeModal = null; }

  // ── Helpers ────────────────────────────────────────────────────────────────

  rruleLabel(r: string): string {
    const preset = RRULE_PRESETS.find(p => p.value === r);
    return preset?.label ?? r;
  }

  statusVariant(active: boolean): 'success' | 'neutral' {
    return active ? 'success' : 'neutral';
  }
}
