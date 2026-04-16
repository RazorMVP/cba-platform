// web-react/src/app/features/operations/api/useCustomers.ts
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/core/api/apiClient'
import type { Customer, ApiResponse } from './types'

// ── Queries ─────────────────────────────────────────────────────

export function useCustomers(params?: { page?: number; size?: number; search?: string; kycStatus?: string }) {
  return useQuery({
    queryKey: ['customers', params],
    queryFn: async () => {
      const { data } = await apiClient.get<ApiResponse<Customer[]>>('/customers', { params })
      return data
    },
  })
}

export function useCustomer(id: string) {
  return useQuery({
    queryKey: ['customers', id],
    queryFn: async () => {
      const { data } = await apiClient.get<ApiResponse<Customer>>(`/customers/${id}`)
      return data.data
    },
    enabled: !!id && id !== 'new',
  })
}

// ── Mutations ────────────────────────────────────────────────────

export function useCreateCustomer() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: Partial<Customer>) => apiClient.post<ApiResponse<Customer>>('/customers', body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['customers'] }),
  })
}

export function useUpdateCustomer(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: Partial<Customer>) => apiClient.put<ApiResponse<Customer>>(`/customers/${id}`, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['customers'] }),
  })
}

export function useCustomerCommand(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ command, body }: { command: string; body?: Record<string, unknown> }) =>
      apiClient.post(`/customers/${id}?command=${command}`, body ?? {}),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['customers', id] }),
  })
}
