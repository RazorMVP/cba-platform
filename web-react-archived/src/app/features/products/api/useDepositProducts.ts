// web-react/src/app/features/products/api/useDepositProducts.ts
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/core/api/apiClient'
import type { ApiResponse, DepositProduct, CreateDepositProductRequest } from './types'

const BASE = '/api/v1/deposit-products'

export function useDepositProducts(params?: { active?: boolean; accountType?: string }) {
  return useQuery({
    queryKey: ['deposit-products', params],
    queryFn: () => apiClient.get<ApiResponse<DepositProduct[]>>(BASE, { params }).then(r => r.data),
  })
}

export function useDepositProduct(id: string) {
  return useQuery({
    queryKey: ['deposit-products', id],
    queryFn: () => apiClient.get<ApiResponse<DepositProduct>>(`${BASE}/${id}`).then(r => r.data),
    enabled: !!id && id !== 'new',
  })
}

export function useCreateDepositProduct() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (req: CreateDepositProductRequest) =>
      apiClient.post<ApiResponse<{ id: string }>>(BASE, req).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['deposit-products'] }),
  })
}

export function useUpdateDepositProduct(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (req: Partial<CreateDepositProductRequest>) =>
      apiClient.put<ApiResponse<DepositProduct>>(`${BASE}/${id}`, req).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['deposit-products'] }),
  })
}

export function useDeleteDepositProduct() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => apiClient.delete(`${BASE}/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['deposit-products'] }),
  })
}
