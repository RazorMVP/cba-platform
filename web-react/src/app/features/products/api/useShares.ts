// web-react/src/app/features/products/api/useShares.ts
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/core/api/apiClient'
import type { ApiResponse, ShareProduct, CreateShareProductRequest } from './types'

const BASE = '/api/v1/shareproducts'

export function useShareProducts(params?: { active?: boolean }) {
  return useQuery({
    queryKey: ['share-products', params],
    queryFn: () => apiClient.get<ApiResponse<ShareProduct[]>>(BASE, { params }).then(r => r.data),
  })
}

export function useShareProduct(id: string) {
  return useQuery({
    queryKey: ['share-products', id],
    queryFn: () => apiClient.get<ApiResponse<ShareProduct>>(`${BASE}/${id}`).then(r => r.data),
    enabled: !!id && id !== 'new',
  })
}

export function useCreateShareProduct() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (req: CreateShareProductRequest) =>
      apiClient.post<ApiResponse<{ id: string }>>(BASE, req).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['share-products'] }),
  })
}

export function useUpdateShareProduct(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (req: Partial<CreateShareProductRequest>) =>
      apiClient.put<ApiResponse<ShareProduct>>(`${BASE}/${id}`, req).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['share-products'] }),
  })
}

export function useDeleteShareProduct() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => apiClient.delete(`${BASE}/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['share-products'] }),
  })
}
