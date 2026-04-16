// web-react/src/app/features/products/api/useLoanProducts.ts
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/core/api/apiClient'
import type { ApiResponse, LoanProduct, CreateLoanProductRequest } from './types'

const BASE = '/api/v1/loan-products'

export function useLoanProducts(params?: { active?: boolean }) {
  return useQuery({
    queryKey: ['loan-products', params],
    queryFn: () => apiClient.get<ApiResponse<LoanProduct[]>>(BASE, { params }).then(r => r.data),
  })
}

export function useLoanProduct(id: string) {
  return useQuery({
    queryKey: ['loan-products', id],
    queryFn: () => apiClient.get<ApiResponse<LoanProduct>>(`${BASE}/${id}`).then(r => r.data),
    enabled: !!id && id !== 'new',
  })
}

export function useCreateLoanProduct() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (req: CreateLoanProductRequest) =>
      apiClient.post<ApiResponse<{ id: string }>>(BASE, req).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['loan-products'] }),
  })
}

export function useUpdateLoanProduct(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (req: Partial<CreateLoanProductRequest>) =>
      apiClient.put<ApiResponse<LoanProduct>>(`${BASE}/${id}`, req).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['loan-products'] }),
  })
}

export function useDeleteLoanProduct() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => apiClient.delete(`${BASE}/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['loan-products'] }),
  })
}
