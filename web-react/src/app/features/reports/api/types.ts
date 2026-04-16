// web-react/src/app/features/reports/api/types.ts

export type OutputType = 'CSV' | 'PDF' | 'XLS'
export type JobStatus = 'RUNNING' | 'IDLE' | 'FAILED' | 'SUCCESS'

// ── Reports ───────────────────────────────────────────────────────────────────

export interface ReportParameter {
  parameterId:     string
  parameterName:   string
  parameterLabel?: string
  parameterType:   string    // 'text' | 'date' | 'select' | 'number'
  defaultValue?:   string
  selectAll?:      string    // SQL for select options
}

export interface Report {
  id:                      string
  reportName:              string
  reportType:              string
  reportSubType?:          string
  reportSql?:              string
  description?:            string
  coreReport:              boolean
  useReport:               boolean
  selfServiceUserReport:   boolean
  reportParameters?:       ReportParameter[]
}

export interface ReportRequest {
  reportName:    string
  reportType:    string
  reportSubType: string
  description:   string
  reportSql:     string
  coreReport:    boolean
  useReport:     boolean
}

// Schema-on-read — columns derived at runtime from first row keys
export type ReportRow = Record<string, unknown>

// ── CoB Scheduler ────────────────────────────────────────────────────────────

export interface CobJob {
  jobName:           string
  displayName:       string
  cronExpression:    string
  jobRunningStatus:  JobStatus
  lastRunStartTime?: string
  lastRunEndTime?:   string
  lastRunStatus?:    string
  nextRunTime?:      string
  currentlyRunning:  boolean
}

export interface CobJobHistory {
  id:            string
  jobName:       string
  startTime:     string
  endTime?:      string
  status:        JobStatus
  errorMessage?: string
  duration?:     number   // ms
}

// ── Report Mailing Jobs ───────────────────────────────────────────────────────

export interface ReportMailingJob {
  id:                   string
  name:                 string
  reportName:           string
  emailRecipients:      string
  emailSubject:         string
  emailMessage?:        string
  recurrence?:          string   // iCal RRULE
  outputType:           OutputType
  params?:              Record<string, string>
  runCount:             number
  previousRunStartTime?: string
  previousRunEndTime?:   string
  previousRunStatus?:    string
  active:               boolean
}

export interface MailingJobRequest {
  name:            string
  reportName:      string
  emailRecipients: string
  emailSubject:    string
  emailMessage?:   string
  recurrence?:     string
  outputType:      OutputType
  params?:         Record<string, string>
}
