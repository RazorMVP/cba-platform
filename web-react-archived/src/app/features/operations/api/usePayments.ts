// web-react/src/app/features/operations/api/usePayments.ts
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/core/api/apiClient'
import type { Payment, ApiResponse } from './types'

export function usePayments(params?: { page?: number; size?: number; sourceAccountId?: string }) {
  return useQuery({
    queryKey: ['payments', params],
    queryFn: async () => {
      const { data } = await apiClient.get<ApiResponse<Payment[]>>('/payments', { params })
      return data
    },
  })
}

export function usePayment(id: string) {
  return useQuery({
    queryKey: ['payments', id],
    queryFn: async () => {
      const { data } = await apiClient.get<ApiResponse<Payment>>(`/payments/${id}`)
      return data.data
    },
    enabled: !!id,
  })
}

export interface TransferRequest {
  sourceAccountId: string
  destinationAccountId: string
  amount: number
  description?: string
}

export function useTransfer() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: TransferRequest) => apiClient.post<ApiResponse<Payment>>('/payments/transfer', body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['payments'] })
      qc.invalidateQueries({ queryKey: ['accounts'] })
    },
  })
}

export function useReversePayment(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => apiClient.post(`/payments/${id}?command=reverse`, {}),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['payments', id] }),
  })
}
