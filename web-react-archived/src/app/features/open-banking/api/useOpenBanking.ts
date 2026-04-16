// web-react/src/app/features/open-banking/api/useOpenBanking.ts
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/core/api/apiClient'
import type { CreateConsentRequest } from './types'

// ── Consents ───────────────────────────────────────────────────────────────────

export function useConsents() {
  return useQuery({
    queryKey: ['consents'],
    queryFn: () => apiClient.get('/open-banking/v3.1/consents').then(r => r.data),
  })
}

export function useConsent(id: string) {
  return useQuery({
    queryKey: ['consents', id],
    queryFn: () => apiClient.get(`/open-banking/v3.1/consents/${id}`).then(r => r.data),
    enabled: !!id,
  })
}

export function useCreateConsent() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: CreateConsentRequest) =>
      apiClient.post('/open-banking/v3.1/consents', body).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['consents'] }),
  })
}

export function useAuthoriseConsent(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () =>
      apiClient.put(`/open-banking/v3.1/consents/${id}/authorise`).then(r => r.data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['consents'] })
      qc.invalidateQueries({ queryKey: ['consents', id] })
    },
  })
}

export function useRevokeConsent(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () =>
      apiClient.delete(`/open-banking/v3.1/consents/${id}`).then(r => r.data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['consents'] })
      qc.invalidateQueries({ queryKey: ['consents', id] })
    },
  })
}
