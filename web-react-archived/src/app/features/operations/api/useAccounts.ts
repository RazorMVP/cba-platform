// web-react/src/app/features/operations/api/useAccounts.ts
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/core/api/apiClient'
import type { Account, Transaction, ApiResponse } from './types'

export function useAccounts(params?: { page?: number; size?: number; accountType?: string }) {
  return useQuery({
    queryKey: ['accounts', params],
    queryFn: async () => {
      const { data } = await apiClient.get<ApiResponse<Account[]>>('/accounts', { params })
      return data
    },
  })
}

export function useAccount(id: string) {
  return useQuery({
    queryKey: ['accounts', id],
    queryFn: async () => {
      const { data } = await apiClient.get<ApiResponse<Account>>(`/accounts/${id}`)
      return data.data
    },
    enabled: !!id && id !== 'new',
  })
}

export function useAccountTransactions(accountId: string, params?: { page?: number; size?: number }) {
  return useQuery({
    queryKey: ['accounts', accountId, 'transactions', params],
    queryFn: async () => {
      const { data } = await apiClient.get<ApiResponse<Transaction[]>>(`/accounts/${accountId}/transactions`, { params })
      return data
    },
    enabled: !!accountId && accountId !== 'new',
  })
}

export function useCreateAccount() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: Partial<Account>) => apiClient.post<ApiResponse<Account>>('/accounts', body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['accounts'] }),
  })
}

export function useAccountCommand(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ command, body }: { command: string; body?: Record<string, unknown> }) =>
      apiClient.post(`/accounts/${id}?command=${command}`, body ?? {}),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['accounts', id] })
      qc.invalidateQueries({ queryKey: ['accounts', id, 'transactions'] })
    },
  })
}
