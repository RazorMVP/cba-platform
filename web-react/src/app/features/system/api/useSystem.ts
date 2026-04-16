// web-react/src/app/features/system/api/useSystem.ts
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/core/api/apiClient'
import type {
  CreateCodeRequest, CreateCodeValueRequest, UpdateCodeValueRequest,
  UpdateGlobalConfigRequest,
  CreateFloatingRateRequest,
  CreateTaxComponentRequest, CreateTaxGroupRequest,
  UpdateAlgorithmRequest,
} from './types'

// ── Codes ──────────────────────────────────────────────────────────────────────

export function useCodes() {
  return useQuery({
    queryKey: ['codes'],
    queryFn: () => apiClient.get('/codes').then(r => r.data),
  })
}

export function useCodeValues(codeId: string) {
  return useQuery({
    queryKey: ['codes', codeId, 'values'],
    queryFn: () => apiClient.get(`/codes/${codeId}/codevalues`).then(r => r.data),
    enabled: !!codeId,
  })
}

export function useCreateCode() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: CreateCodeRequest) =>
      apiClient.post('/codes', body).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['codes'] }),
  })
}

export function useDeleteCode(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => apiClient.delete(`/codes/${id}`).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['codes'] }),
  })
}

export function useCreateCodeValue(codeId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: CreateCodeValueRequest) =>
      apiClient.post(`/codes/${codeId}/codevalues`, body).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['codes', codeId, 'values'] }),
  })
}

export function useUpdateCodeValue(codeId: string, valueId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: UpdateCodeValueRequest) =>
      apiClient.put(`/codes/${codeId}/codevalues/${valueId}`, body).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['codes', codeId, 'values'] }),
  })
}

export function useDeleteCodeValue(codeId: string, valueId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () =>
      apiClient.delete(`/codes/${codeId}/codevalues/${valueId}`).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['codes', codeId, 'values'] }),
  })
}

// ── Global Configuration ───────────────────────────────────────────────────────

export function useGlobalConfigurations() {
  return useQuery({
    queryKey: ['configurations'],
    queryFn: () => apiClient.get('/configurations').then(r => r.data),
  })
}

export function useUpdateGlobalConfig(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: UpdateGlobalConfigRequest) =>
      apiClient.put(`/configurations/${id}`, body).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['configurations'] }),
  })
}

// ── Floating Rates ─────────────────────────────────────────────────────────────

export function useFloatingRates() {
  return useQuery({
    queryKey: ['floatingrates'],
    queryFn: () => apiClient.get('/floatingrates').then(r => r.data),
  })
}

export function useCreateFloatingRate() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: CreateFloatingRateRequest) =>
      apiClient.post('/floatingrates', body).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['floatingrates'] }),
  })
}

export function useUpdateFloatingRate(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: CreateFloatingRateRequest) =>
      apiClient.put(`/floatingrates/${id}`, body).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['floatingrates'] }),
  })
}

export function useDeleteFloatingRate(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => apiClient.delete(`/floatingrates/${id}`).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['floatingrates'] }),
  })
}

// ── Taxes ──────────────────────────────────────────────────────────────────────

export function useTaxComponents() {
  return useQuery({
    queryKey: ['taxes', 'components'],
    queryFn: () => apiClient.get('/taxes/components').then(r => r.data),
  })
}

export function useCreateTaxComponent() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: CreateTaxComponentRequest) =>
      apiClient.post('/taxes/components', body).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['taxes', 'components'] }),
  })
}

export function useUpdateTaxComponent(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: CreateTaxComponentRequest) =>
      apiClient.put(`/taxes/components/${id}`, body).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['taxes', 'components'] }),
  })
}

export function useTaxGroups() {
  return useQuery({
    queryKey: ['taxes', 'groups'],
    queryFn: () => apiClient.get('/taxes/groups').then(r => r.data),
  })
}

export function useCreateTaxGroup() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: CreateTaxGroupRequest) =>
      apiClient.post('/taxes/groups', body).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['taxes', 'groups'] }),
  })
}

export function useUpdateTaxGroup(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: CreateTaxGroupRequest) =>
      apiClient.put(`/taxes/groups/${id}`, body).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['taxes', 'groups'] }),
  })
}

// ── Account Number Algorithms ──────────────────────────────────────────────────

export function useTenantAlgorithm(tenantId: string) {
  return useQuery({
    queryKey: ['tenants', tenantId, 'account-algorithm'],
    queryFn: () => apiClient.get(`/tenants/${tenantId}/account-algorithm`).then(r => r.data),
    enabled: !!tenantId,
  })
}

export function useUpdateTenantAlgorithm(tenantId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: UpdateAlgorithmRequest) =>
      apiClient.put(`/tenants/${tenantId}/account-algorithm`, body).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['tenants', tenantId, 'account-algorithm'] }),
  })
}
