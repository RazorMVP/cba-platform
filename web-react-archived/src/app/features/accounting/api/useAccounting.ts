// web-react/src/app/features/accounting/api/useAccounting.ts
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/core/api/apiClient'
import type {
  GlAccount, GlAccountRequest,
  JournalEntry, ManualJournalRequest,
  ProvisioningCriteria, ProvisioningCriteriaRequest,
  FinancialActivityAccount, FinancialActivityRequest,
} from './types'

// ── GL Accounts ───────────────────────────────────────────────────────────────

export function useGlAccounts() {
  return useQuery({
    queryKey: ['gl-accounts'],
    queryFn: () => apiClient.get<{ data: GlAccount[] }>('/glaccounts').then(r => r.data),
  })
}

export function useCreateGlAccount() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (req: GlAccountRequest) =>
      apiClient.post<{ data: GlAccount }>('/glaccounts', req).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['gl-accounts'] }),
  })
}

export function useUpdateGlAccount(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (req: GlAccountRequest) =>
      apiClient.put<{ data: GlAccount }>(`/glaccounts/${id}`, req).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['gl-accounts'] }),
  })
}

export function useGlAccountCommand(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (command: 'enable' | 'disable') =>
      apiClient.post(`/glaccounts/${id}?command=${command}`, {}).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['gl-accounts'] }),
  })
}

// ── Journal Entries ────────────────────────────────────────────────────────────

export function useJournalEntries(from?: string, to?: string) {
  return useQuery({
    queryKey: ['journal-entries', from, to],
    queryFn: () => {
      const params: Record<string, string> = {}
      if (from) params.fromDate = from
      if (to)   params.toDate = to
      return apiClient.get<{ data: JournalEntry[] }>('/journalentries', { params }).then(r => r.data)
    },
  })
}

export function useCreateManualJournalEntry() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (req: ManualJournalRequest) =>
      apiClient.post<{ data: { transactionId: string } }>('/journalentries', req).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['journal-entries'] }),
  })
}

export function useReverseJournalEntry() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) =>
      apiClient.post(`/journalentries/${id}/reverse`, {}).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['journal-entries'] }),
  })
}

// ── Provisioning Criteria ─────────────────────────────────────────────────────

export function useProvisioningCriteria() {
  return useQuery({
    queryKey: ['provisioning-criteria'],
    queryFn: () => apiClient.get<{ data: ProvisioningCriteria[] }>('/provisioningcriteria').then(r => r.data),
  })
}

export function useCreateProvisioningCriteria() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (req: ProvisioningCriteriaRequest) =>
      apiClient.post<{ data: ProvisioningCriteria }>('/provisioningcriteria', req).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['provisioning-criteria'] }),
  })
}

export function useUpdateProvisioningCriteria(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (req: ProvisioningCriteriaRequest) =>
      apiClient.put<{ data: ProvisioningCriteria }>(`/provisioningcriteria/${id}`, req).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['provisioning-criteria'] }),
  })
}

export function useDeleteProvisioningCriteria() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => apiClient.delete(`/provisioningcriteria/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['provisioning-criteria'] }),
  })
}

// ── Financial Activity Accounts ────────────────────────────────────────────────

export function useFinancialActivityAccounts() {
  return useQuery({
    queryKey: ['financial-activity-accounts'],
    queryFn: () =>
      apiClient.get<{ data: FinancialActivityAccount[] }>('/financialactivityaccounts').then(r => r.data),
  })
}

export function useCreateFinancialActivityAccount() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (req: FinancialActivityRequest) =>
      apiClient.post<{ data: FinancialActivityAccount }>('/financialactivityaccounts', req).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['financial-activity-accounts'] }),
  })
}

export function useUpdateFinancialActivityAccount(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (req: FinancialActivityRequest) =>
      apiClient.put<{ data: FinancialActivityAccount }>(`/financialactivityaccounts/${id}`, req).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['financial-activity-accounts'] }),
  })
}

export function useDeleteFinancialActivityAccount() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => apiClient.delete(`/financialactivityaccounts/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['financial-activity-accounts'] }),
  })
}
