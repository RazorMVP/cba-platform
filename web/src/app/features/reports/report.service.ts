import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { HttpClient, HttpParams } from '@angular/common/http';
import { ApiService } from '../../core/api/api.service';
import { ApiResponse } from '../../core/models/api-response.model';
import { environment } from '../../../environments/environment';

// ── Reports ────────────────────────────────────────────────────────────────────

/** The API's report categories (Report.Category on the backend). */
export type ReportCategory = 'LOAN' | 'SAVINGS' | 'TELLER' | 'CUSTOMER' | 'ACCOUNTING' | 'MIXED';

/** Display labels, in the order the create form offers them. */
export const REPORT_CATEGORY_LABELS: Record<ReportCategory, string> = {
  LOAN: 'Loans', SAVINGS: 'Savings', TELLER: 'Teller', CUSTOMER: 'Customers',
  ACCOUNTING: 'Accounting', MIXED: 'General',
};

export interface ReportParameter {
  id: string;
  parameterName: string;
  parameterType: 'STRING' | 'DATE' | 'NUMBER' | 'BOOLEAN';
  parameterLabel?: string;
  defaultValue?: string;
  optional: boolean;
  sortOrder?: number;
}

export interface Report {
  id: string;
  reportName: string;
  /** TABLE | CHART | SMS | PENTAHO */
  reportType: string;
  reportCategory?: ReportCategory;
  description?: string;
  reportSql?: string;
  coreReport: boolean;
  useReport: boolean;
  reportParameters: ReportParameter[];
  /** Who created it — a custom report must be enabled by a different admin. */
  createdBy?: string | null;
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
  businessDate?: string;
  startTime: string;
  endTime?: string;
  status: 'SUCCESS' | 'FAILED' | 'RUNNING';
  errorMessage?: string | null;
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

/** A custom report (ADMIN). SQL: one SELECT over the reporting views, parameters as :name. */
export interface ReportRequest {
  reportName: string;
  reportCategory: ReportCategory;
  description?: string;
  reportSql: string;
  reportParameters: {
    parameterName: string;
    parameterLabel?: string;
    parameterType: 'STRING' | 'DATE' | 'NUMBER' | 'BOOLEAN';
    defaultValue?: string;
    optional: boolean;
  }[];
}

/** A report run: the rows, and whether there were more than the server's row cap. */
export interface ReportRun {
  rows: Record<string, unknown>[];
  truncated: boolean;
}

@Injectable({ providedIn: 'root' })
export class ReportService {
  private readonly api = inject(ApiService);
  private readonly http = inject(HttpClient);

  // Reports CRUD
  listReports(): Observable<Report[]> {
    return this.api.get<Report[]>('/reports');
  }
  getReport(id: string): Observable<Report> {
    return this.api.get<Report>(`/reports/${id}`);
  }
  deleteReport(id: string): Observable<void> {
    return this.api.delete<void>(`/reports/${id}`);
  }

  /** Custom report — saved disabled; another admin enables it. */
  createReport(req: ReportRequest): Observable<Report> {
    return this.api.post<Report>('/reports', req);
  }
  enableReport(id: string): Observable<Report> {
    return this.api.post<Report>(`/reports/${id}/enable`, {});
  }

  /** Run a report: rows plus the server's X-Report-Truncated flag (row cap reached). */
  runReport(reportName: string, params: Record<string, string>): Observable<ReportRun> {
    return this.http.get<ApiResponse<Record<string, unknown>[]>>(
      `${environment.apiBaseUrl}/runreports/${encodeURIComponent(reportName)}`,
      { params: new HttpParams({ fromObject: params }), observe: 'response' },
    ).pipe(map(r => ({
      rows: r.body?.data ?? [],
      truncated: r.headers.get('X-Report-Truncated') === 'true',
    })));
  }

  /**
   * Export through HttpClient (so the bearer token goes with it) as a file; `window.open` sent none and
   * failed once login was enforced.
   */
  exportReport(reportName: string, format: 'csv' | 'xlsx' | 'pdf', params: Record<string, string>): Observable<Blob> {
    return this.http.get(`${environment.apiBaseUrl}/runreports/${encodeURIComponent(reportName)}/export`,
      { params: new HttpParams({ fromObject: { ...params, format } }), responseType: 'blob' });
  }

  // CoB Scheduler
  listJobs(): Observable<CobJob[]> {
    return this.api.get<CobJob[]>('/jobs');
  }
  /** Runs synchronously; resolves with the finished run (status SUCCESS or FAILED). */
  runJob(jobName: string): Observable<CobJobHistory> {
    return this.api.post<CobJobHistory>(`/jobs/${encodeURIComponent(jobName)}/run`, {});
  }
  getJobHistory(jobName: string): Observable<CobJobHistory[]> {
    return this.api.get<CobJobHistory[]>(`/jobs/${encodeURIComponent(jobName)}/history`);
  }

  // Report Mailing Jobs — backend returns Page<ReportMailingJob>; extract content array
  listMailingJobs(): Observable<ReportMailingJob[]> {
    return this.api.getPage<ReportMailingJob>('/reportmailingjobs').pipe(
      map(page => page.content ?? [])
    );
  }
  createMailingJob(req: ReportMailingRequest): Observable<ReportMailingJob> {
    return this.api.post<ReportMailingJob>('/reportmailingjobs', req);
  }
  updateMailingJob(id: string, req: ReportMailingRequest): Observable<ReportMailingJob> {
    return this.api.put<ReportMailingJob>(`/reportmailingjobs/${id}`, req);
  }
  deleteMailingJob(id: string): Observable<void> {
    return this.api.delete<void>(`/reportmailingjobs/${id}`);
  }
  runMailingJob(id: string): Observable<void> {
    return this.api.command<void>(`/reportmailingjobs/${id}`, 'run');
  }
}
