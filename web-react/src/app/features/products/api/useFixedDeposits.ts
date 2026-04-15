// web-react/src/app/features/products/api/useFixedDeposits.ts
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/core/api/apiClient'
import type { ApiResponse, FixedDepositProduct, CreateFixedDepositProductRequest } from './types'

const BASE = '/api/v1/fixeddepositproducts'

export function useFixedDepositProducts(params?: { active?: boolean }) {
  return useQuery({
    queryKey: ['fixed-deposit-products', params],
    queryFn: () => apiClient.get<ApiResponse<FixedDepositProduct[]>>(BASE, { params }).then(r => r.data),
  })
}

export function useFixedDepositProduct(id: string) {
  return useQuery({
    queryKey: ['fixed-deposit-products', id],
    queryFn: () => apiClient.get<ApiResponse<FixedDepositProduct>>(`${BASE}/${id}`).then(r => r.data),
    enabled: !!id && id !== 'new',
  })
}

export function useCreateFixedDepositProduct() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (req: CreateFixedDepositProductRequest) =>
      apiClient.post<ApiResponse<{ id: string }>>(BASE, req).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['fixed-deposit-products'] }),
  })
}

export function useUpdateFixedDepositProduct(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (req: Partial<CreateFixedDepositProductRequest>) =>
      apiClient.put<ApiResponse<FixedDepositProduct>>(`${BASE}/${id}`, req).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['fixed-deposit-products'] }),
  })
}

export function useDeleteFixedDepositProduct() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => apiClient.delete(`${BASE}/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['fixed-deposit-products'] }),
  })
}
