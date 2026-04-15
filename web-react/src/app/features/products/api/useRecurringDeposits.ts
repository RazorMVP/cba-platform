// web-react/src/app/features/products/api/useRecurringDeposits.ts
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/core/api/apiClient'
import type { ApiResponse, RecurringDepositProduct, CreateRecurringDepositProductRequest } from './types'

const BASE = '/api/v1/recurringdepositproducts'

export function useRecurringDepositProducts(params?: { active?: boolean }) {
  return useQuery({
    queryKey: ['recurring-deposit-products', params],
    queryFn: () => apiClient.get<ApiResponse<RecurringDepositProduct[]>>(BASE, { params }).then(r => r.data),
  })
}

export function useRecurringDepositProduct(id: string) {
  return useQuery({
    queryKey: ['recurring-deposit-products', id],
    queryFn: () => apiClient.get<ApiResponse<RecurringDepositProduct>>(`${BASE}/${id}`).then(r => r.data),
    enabled: !!id && id !== 'new',
  })
}

export function useCreateRecurringDepositProduct() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (req: CreateRecurringDepositProductRequest) =>
      apiClient.post<ApiResponse<{ id: string }>>(BASE, req).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['recurring-deposit-products'] }),
  })
}

export function useUpdateRecurringDepositProduct(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (req: Partial<CreateRecurringDepositProductRequest>) =>
      apiClient.put<ApiResponse<RecurringDepositProduct>>(`${BASE}/${id}`, req).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['recurring-deposit-products'] }),
  })
}

export function useDeleteRecurringDepositProduct() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => apiClient.delete(`${BASE}/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['recurring-deposit-products'] }),
  })
}
