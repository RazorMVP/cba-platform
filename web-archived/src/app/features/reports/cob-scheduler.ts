import { Component, inject, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ReportService, CobJob, CobJobHistory } from './report.service';

@Component({
  selector: 'app-cob-scheduler',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './cob-scheduler.html',
  styleUrl: './cob-scheduler.scss',
})
export class CobSchedulerComponent implements OnInit, OnDestroy {
  private readonly svc = inject(ReportService);
  private refreshTimer: ReturnType<typeof setTimeout> | null = null;

  jobs:    CobJob[] = [];
  loading  = true;
  error    = '';

  // History panel
  selectedJob:    CobJob | null = null;
  history:        CobJobHistory[] = [];
  historyLoading  = false;

  // Per-job run state
  runningJobs = new Set<string>();
  runErrors   = new Map<string, string>();

  ngOnInit(): void {
    this.loadJobs();
  }

  ngOnDestroy(): void {
    if (this.refreshTimer) clearTimeout(this.refreshTimer);
  }

  loadJobs(): void {
    this.svc.listJobs().subscribe({
      next: list => { this.jobs = list; this.loading = false; },
      error: () => { this.error = 'Failed to load CoB jobs.'; this.loading = false; },
    });
  }

  runJob(job: CobJob): void {
    const key = job.jobName;
    this.runningJobs.add(key);
    this.runErrors.delete(key);
    this.svc.runJob(key).subscribe({
      next: () => {
        this.runningJobs.delete(key);
        // Refresh job list after a short delay to reflect status update
        this.refreshTimer = setTimeout(() => this.loadJobs(), 2500);
        // Also reload history if this job is selected
        if (this.selectedJob?.jobName === key) {
          this.loadHistory(job);
        }
      },
      error: () => {
        this.runErrors.set(key, 'Failed to trigger job.');
        this.runningJobs.delete(key);
      },
    });
  }

  selectJob(job: CobJob): void {
    if (this.selectedJob?.jobName === job.jobName) {
      this.selectedJob = null;
      this.history     = [];
      return;
    }
    this.selectedJob = job;
    this.loadHistory(job);
  }

  loadHistory(job: CobJob): void {
    this.historyLoading = true;
    this.svc.getJobHistory(job.jobName).subscribe({
      next: h => {
        this.history        = h.sort((a, b) => b.startTime.localeCompare(a.startTime));
        this.historyLoading = false;
      },
      error: () => { this.historyLoading = false; },
    });
  }

  // ── Helpers ────────────────────────────────────────────────────────────────

  isRunning(job: CobJob): boolean {
    return this.runningJobs.has(job.jobName) || job.currentlyRunning;
  }

  runError(job: CobJob): string {
    return this.runErrors.get(job.jobName) ?? '';
  }

  statusVariant(s: string | null | undefined): 'success' | 'warning' | 'neutral' | 'error' {
    if (!s) return 'neutral';
    if (s === 'SUCCESS') return 'success';
    if (s === 'FAILED')  return 'error';
    if (s === 'RUNNING') return 'warning';
    return 'neutral';
  }

  duration(h: CobJobHistory): string {
    if (!h.endTime) return 'Running…';
    const ms = new Date(h.endTime).getTime() - new Date(h.startTime).getTime();
    if (ms < 1000)   return `${ms}ms`;
    if (ms < 60000)  return `${(ms / 1000).toFixed(1)}s`;
    return `${Math.floor(ms / 60000)}m ${Math.round((ms % 60000) / 1000)}s`;
  }
}
