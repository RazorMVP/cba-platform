// web-react/src/app/features/operations/api/useTellers.ts
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/core/api/apiClient'
import type { Teller, Cashier, TellerSession, ApiResponse } from './types'

export function useTellers(params?: { page?: number; size?: number; status?: string }) {
  return useQuery({
    queryKey: ['tellers', params],
    queryFn: async () => {
      const { data } = await apiClient.get<ApiResponse<Teller[]>>('/tellers', { params })
      return data
    },
  })
}

export function useTeller(id: string) {
  return useQuery({
    queryKey: ['tellers', id],
    queryFn: async () => {
      const { data } = await apiClient.get<ApiResponse<Teller>>(`/tellers/${id}`)
      return data.data
    },
    enabled: !!id,
  })
}

export function useTellerCashiers(tellerId: string) {
  return useQuery({
    queryKey: ['tellers', tellerId, 'cashiers'],
    queryFn: async () => {
      const { data } = await apiClient.get<ApiResponse<Cashier[]>>(`/tellers/${tellerId}/cashiers`)
      return data
    },
    enabled: !!tellerId,
  })
}

export function useTellerSessions(tellerId: string) {
  return useQuery({
    queryKey: ['tellers', tellerId, 'sessions'],
    queryFn: async () => {
      const { data } = await apiClient.get<ApiResponse<TellerSession[]>>(`/tellers/${tellerId}/sessions`)
      return data
    },
    enabled: !!tellerId,
  })
}

export function useCreateTeller() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: Partial<Teller>) => apiClient.post<ApiResponse<Teller>>('/tellers', body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['tellers'] }),
  })
}

export function useTellerCommand(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ command }: { command: string }) => apiClient.post(`/tellers/${id}/${command}`, {}),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['tellers', id] }),
  })
}

export function useSessionTransaction(tellerId: string, sessionId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post(`/tellers/${tellerId}/sessions/${sessionId}/transactions`, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['tellers', tellerId, 'sessions'] }),
  })
}

export function useSettleSession(tellerId: string, sessionId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: { actualCash: number }) =>
      apiClient.post(`/tellers/${tellerId}/sessions/${sessionId}/settle`, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['tellers', tellerId, 'sessions'] }),
  })
}
