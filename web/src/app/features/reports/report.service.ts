import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from '../../core/api/api.service';

// ── Reports ────────────────────────────────────────────────────────────────────

export interface ReportParameter {
  id: string;
  parameterName: string;
  parameterType: 'STRING' | 'DATE' | 'NUMBER' | 'BOOLEAN';
  parameterLabel?: string;
  defaultValue?: string;
  optional: boolean;
}

export interface Report {
  id: string;
  reportName: string;
  reportType: string;
  reportCategory?: string;
  description?: string;
  reportSql?: string;
  coreReport: boolean;
  useReport: boolean;
  reportParameters: ReportParameter[];
}

export interface ReportRequest {
  reportName: string;
  reportType: string;
  reportCategory?: string;
  description?: string;
  reportSql: string;
  reportParameters?: Omit<ReportParameter, 'id'>[];
}

// ── CoB Scheduler ──────────────────────────────────────────────────────────────

export interface CobJob {
  jobId?: string;
  jobName: string;
  displayName: string;
  cronExpression?: string;
  nextRunTime?: string;
  previousRunStartTime?: string;
  previousRunEndTime?: string;
  previousRunStatus?: 'SUCCESS' | 'FAILED' | 'RUNNING' | null;
  active: boolean;
  currentlyRunning: boolean;
}

export interface CobJobHistory {
  id: string;
  jobName: string;
  startTime: string;
  endTime?: string;
  status: 'SUCCESS' | 'FAILED' | 'RUNNING';
  errorMessage?: string;
}

// ── Report Mailing ─────────────────────────────────────────────────────────────

export type MailingOutputType = 'CSV' | 'PDF' | 'XLS';

export interface ReportMailingJob {
  id: string;
  name: string;
  reportName: string;
  reportParams?: Record<string, string>;
  emailRecipients: string;
  emailSubject: string;
  emailMessage?: string;
  recurrence: string;
  outputType: MailingOutputType;
  isActive: boolean;
  runCount: number;
  previousRunStartTime?: string;
  previousRunEndTime?: string;
  previousRunStatus?: string;
}

export interface ReportMailingRequest {
  name: string;
  reportName: string;
  reportParams?: Record<string, string>;
  emailRecipients: string;
  emailSubject: string;
  emailMessage?: string;
  recurrence: string;
  outputType: MailingOutputType;
  isActive: boolean;
}

// ── Service ────────────────────────────────────────────────────────────────────

@Injectable({ providedIn: 'root' })
export class ReportService {
  private readonly api = inject(ApiService);

  // Reports CRUD
  listReports(): Observable<Report[]> {
    return this.api.get<Report[]>('/api/v1/reports');
  }
  getReport(id: string): Observable<Report> {
    return this.api.get<Report>(`/api/v1/reports/${id}`);
  }
  createReport(req: ReportRequest): Observable<Report> {
    return this.api.post<Report>('/api/v1/reports', req);
  }
  deleteReport(id: string): Observable<void> {
    return this.api.delete<void>(`/api/v1/reports/${id}`);
  }

  // Run report — returns array of arbitrary row objects
  runReport(reportName: string, params: Record<string, string>): Observable<Record<string, unknown>[]> {
    return this.api.get<Record<string, unknown>[]>(`/api/v1/runreports/${encodeURIComponent(reportName)}`, params);
  }

  // CoB Scheduler
  listJobs(): Observable<CobJob[]> {
    return this.api.get<CobJob[]>('/api/v1/jobs');
  }
  runJob(jobName: string): Observable<void> {
    return this.api.post<void>(`/api/v1/jobs/${encodeURIComponent(jobName)}/run`, {});
  }
  getJobHistory(jobName: string): Observable<CobJobHistory[]> {
    return this.api.get<CobJobHistory[]>(`/api/v1/jobs/${encodeURIComponent(jobName)}/history`);
  }

  // Report Mailing Jobs
  listMailingJobs(): Observable<ReportMailingJob[]> {
    return this.api.get<ReportMailingJob[]>('/api/v1/reportmailingjobs');
  }
  createMailingJob(req: ReportMailingRequest): Observable<ReportMailingJob> {
    return this.api.post<ReportMailingJob>('/api/v1/reportmailingjobs', req);
  }
  updateMailingJob(id: string, req: ReportMailingRequest): Observable<ReportMailingJob> {
    return this.api.put<ReportMailingJob>(`/api/v1/reportmailingjobs/${id}`, req);
  }
  deleteMailingJob(id: string): Observable<void> {
    return this.api.delete<void>(`/api/v1/reportmailingjobs/${id}`);
  }
  runMailingJob(id: string): Observable<void> {
    return this.api.command<void>(`/api/v1/reportmailingjobs/${id}`, 'run');
  }
}
