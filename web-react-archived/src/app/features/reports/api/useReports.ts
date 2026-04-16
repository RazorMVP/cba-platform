// web-react/src/app/features/reports/api/useReports.ts
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/core/api/apiClient'
import type {
  Report, ReportRequest, ReportRow,
  CobJob, CobJobHistory,
  ReportMailingJob, MailingJobRequest,
} from './types'

// ── Reports ────────────────────────────────────────────────────────────────────

export function useReports() {
  return useQuery({
    queryKey: ['reports'],
    queryFn: () => apiClient.get<{ data: Report[] }>('/reports').then(r => r.data),
  })
}

export function useReport(id: string) {
  return useQuery({
    queryKey: ['reports', id],
    queryFn: () => apiClient.get<{ data: Report }>(`/reports/${id}`).then(r => r.data),
    enabled: !!id,
  })
}

export function useCreateReport() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (req: ReportRequest) =>
      apiClient.post<{ data: Report }>('/reports', req).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['reports'] }),
  })
}

export function useDeleteReport(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => apiClient.delete(`/reports/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['reports'] }),
  })
}

// Run a report — params are key/value pairs from the dynamic parameter form
export function useRunReport(reportName: string) {
  return useMutation({
    mutationFn: (params: Record<string, string>) =>
      apiClient
        .get<{ data: ReportRow[] }>(`/runreports/${encodeURIComponent(reportName)}`, { params })
        .then(r => r.data),
  })
}

// ── CoB Scheduler ─────────────────────────────────────────────────────────────

export function useCobJobs() {
  return useQuery({
    queryKey: ['cob-jobs'],
    queryFn: () => apiClient.get<{ data: CobJob[] }>('/jobs').then(r => r.data),
  })
}

export function useCobJobHistory(jobName: string) {
  return useQuery({
    queryKey: ['cob-history', jobName],
    queryFn: () =>
      apiClient.get<{ data: CobJobHistory[] }>(`/jobs/${jobName}/history`).then(r => r.data),
    enabled: !!jobName,
  })
}

export function useRunCobJob(jobName: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => apiClient.post(`/jobs/${jobName}/run`, {}).then(r => r.data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['cob-jobs'] })
      qc.invalidateQueries({ queryKey: ['cob-history', jobName] })
    },
  })
}

// ── Report Mailing Jobs ────────────────────────────────────────────────────────

export function useMailingJobs() {
  return useQuery({
    queryKey: ['mailing-jobs'],
    queryFn: () =>
      apiClient.get<{ data: ReportMailingJob[] }>('/reportmailingjobs').then(r => r.data),
  })
}

export function useCreateMailingJob() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (req: MailingJobRequest) =>
      apiClient.post<{ data: ReportMailingJob }>('/reportmailingjobs', req).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['mailing-jobs'] }),
  })
}

export function useUpdateMailingJob(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (req: MailingJobRequest) =>
      apiClient
        .put<{ data: ReportMailingJob }>(`/reportmailingjobs/${id}`, req)
        .then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['mailing-jobs'] }),
  })
}

export function useDeleteMailingJob(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => apiClient.delete(`/reportmailingjobs/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['mailing-jobs'] }),
  })
}

export function useRunMailingJob(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () =>
      apiClient.post(`/reportmailingjobs/${id}?command=run`, {}).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['mailing-jobs'] }),
  })
}
