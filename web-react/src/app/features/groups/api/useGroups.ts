// web-react/src/app/features/groups/api/useGroups.ts
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/core/api/apiClient'
import type {
  CreateGroupRequest, GenerateCollectionSheetRequest, CreateCenterRequest,
} from './types'

// ── Groups ─────────────────────────────────────────────────────────────────────

export function useGroups() {
  return useQuery({
    queryKey: ['groups'],
    queryFn: () => apiClient.get('/groups').then(r => r.data),
  })
}

export function useGroup(id: string) {
  return useQuery({
    queryKey: ['groups', id],
    queryFn: () => apiClient.get(`/groups/${id}`).then(r => r.data),
    enabled: !!id,
  })
}

export function useCreateGroup() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: CreateGroupRequest) =>
      apiClient.post('/groups', body).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['groups'] }),
  })
}

export function useActivateGroup(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () =>
      apiClient.post(`/groups/${id}?command=activate`).then(r => r.data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['groups'] })
      qc.invalidateQueries({ queryKey: ['groups', id] })
    },
  })
}

export function useGroupMembers(groupId: string) {
  return useQuery({
    queryKey: ['groups', groupId, 'members'],
    queryFn: () => apiClient.get(`/groups/${groupId}/members`).then(r => r.data),
    enabled: !!groupId,
  })
}

export function useAddGroupMember(groupId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (customerId: string) =>
      apiClient.post(`/groups/${groupId}/members/${customerId}`).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['groups', groupId, 'members'] }),
  })
}

export function useRemoveGroupMember(groupId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (customerId: string) =>
      apiClient.delete(`/groups/${groupId}/members/${customerId}`).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['groups', groupId, 'members'] }),
  })
}

export function useGlimAccounts(groupId: string) {
  return useQuery({
    queryKey: ['groups', groupId, 'glimaccounts'],
    queryFn: () => apiClient.get(`/groups/${groupId}/glimaccounts`).then(r => r.data),
    enabled: !!groupId,
  })
}

export function useGenerateCollectionSheet() {
  return useMutation({
    mutationFn: (body: GenerateCollectionSheetRequest) =>
      apiClient.post('/collectionsheets', body).then(r => r.data),
  })
}

export function useAssignGroupStaff(groupId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (staffId: string) =>
      apiClient.post(`/groups/${groupId}?command=assignStaff`, { staffId }).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['groups', groupId] }),
  })
}

export function useUnassignGroupStaff(groupId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () =>
      apiClient.post(`/groups/${groupId}?command=unassignStaff`).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['groups', groupId] }),
  })
}

// ── Centers ────────────────────────────────────────────────────────────────────

export function useCenters() {
  return useQuery({
    queryKey: ['centers'],
    queryFn: () => apiClient.get('/centers').then(r => r.data),
  })
}

export function useCenter(id: string) {
  return useQuery({
    queryKey: ['centers', id],
    queryFn: () => apiClient.get(`/centers/${id}`).then(r => r.data),
    enabled: !!id,
  })
}

export function useCreateCenter() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: CreateCenterRequest) =>
      apiClient.post('/centers', body).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['centers'] }),
  })
}

export function useActivateCenter(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () =>
      apiClient.post(`/centers/${id}?command=activate`).then(r => r.data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['centers'] })
      qc.invalidateQueries({ queryKey: ['centers', id] })
    },
  })
}

export function useCenterGroups(centerId: string) {
  return useQuery({
    queryKey: ['centers', centerId, 'groups'],
    queryFn: () => apiClient.get(`/centers/${centerId}/groups`).then(r => r.data),
    enabled: !!centerId,
  })
}

export function useCenterMembers(centerId: string) {
  return useQuery({
    queryKey: ['centers', centerId, 'members'],
    queryFn: () => apiClient.get(`/centers/${centerId}/members`).then(r => r.data),
    enabled: !!centerId,
  })
}
