// web-react/src/app/features/admin/api/useAdmin.ts
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/core/api/apiClient'
import type {
  PlatformUser, CreateUserRequest,
  Role, CreateRoleRequest, UpdatePermissionsRequest, Permission,
  Office, CreateOfficeRequest,
  Hook, CreateHookRequest,
  MakerCheckerEntry, MakerCheckerStatus,
  NotificationTemplate, NotificationLog, CreateTemplateRequest,
  TppRegistration, RegisterTppRequest,
} from './types'

// ── Users ──────────────────────────────────────────────────────────────────────

export function useUsers() {
  return useQuery({
    queryKey: ['users'],
    queryFn: () => apiClient.get<{ data: PlatformUser[] }>('/users').then(r => r.data),
  })
}

export function useCreateUser() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (req: CreateUserRequest) =>
      apiClient.post<{ data: PlatformUser }>('/users', req).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['users'] }),
  })
}

export function useEnableUser(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () =>
      apiClient.post(`/users/${id}?command=enable`, {}).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['users'] }),
  })
}

export function useDisableUser(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () =>
      apiClient.post(`/users/${id}?command=disable`, {}).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['users'] }),
  })
}

export function useDeleteUser(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => apiClient.delete(`/users/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['users'] }),
  })
}

// ── Roles ──────────────────────────────────────────────────────────────────────

export function useRoles() {
  return useQuery({
    queryKey: ['roles'],
    queryFn: () => apiClient.get<{ data: Role[] }>('/roles').then(r => r.data),
  })
}

export function useAllPermissions() {
  return useQuery({
    queryKey: ['permissions'],
    queryFn: () =>
      apiClient.get<{ data: Permission[] }>('/roles/permissions').then(r => r.data),
  })
}

export function useCreateRole() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (req: CreateRoleRequest) =>
      apiClient.post<{ data: Role }>('/roles', req).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['roles'] }),
  })
}

export function useUpdateRole(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (req: CreateRoleRequest) =>
      apiClient.put<{ data: Role }>(`/roles/${id}`, req).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['roles'] }),
  })
}

export function useUpdateRolePermissions(roleId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (req: UpdatePermissionsRequest) =>
      apiClient.put<{ data: Role }>(`/roles/${roleId}/permissions`, req).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['roles'] }),
  })
}

export function useDeleteRole(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => apiClient.delete(`/roles/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['roles'] }),
  })
}

// ── Offices ────────────────────────────────────────────────────────────────────

export function useOffices() {
  return useQuery({
    queryKey: ['offices'],
    queryFn: () => apiClient.get<{ data: Office[] }>('/offices').then(r => r.data),
  })
}

export function useCreateOffice() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (req: CreateOfficeRequest) =>
      apiClient.post<{ data: Office }>('/offices', req).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['offices'] }),
  })
}

export function useUpdateOffice(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (req: CreateOfficeRequest) =>
      apiClient.put<{ data: Office }>(`/offices/${id}`, req).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['offices'] }),
  })
}

// ── Hooks ──────────────────────────────────────────────────────────────────────

export function useHooks() {
  return useQuery({
    queryKey: ['hooks'],
    queryFn: () => apiClient.get<{ data: Hook[] }>('/hooks').then(r => r.data),
  })
}

export function useCreateHook() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (req: CreateHookRequest) =>
      apiClient.post<{ data: Hook }>('/hooks', req).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['hooks'] }),
  })
}

export function useUpdateHook(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (req: CreateHookRequest) =>
      apiClient.put<{ data: Hook }>(`/hooks/${id}`, req).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['hooks'] }),
  })
}

export function useDeleteHook(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => apiClient.delete(`/hooks/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['hooks'] }),
  })
}

// ── Maker-Checker ──────────────────────────────────────────────────────────────

export function useMakerCheckerEntries(status?: MakerCheckerStatus) {
  return useQuery({
    queryKey: ['maker-checker', status ?? 'ALL'],
    queryFn: () => {
      const params = status ? { status } : undefined
      return apiClient
        .get<{ data: MakerCheckerEntry[] }>('/makercheckers', { params })
        .then(r => r.data)
    },
  })
}

export function useApproveMakerChecker(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () =>
      apiClient.post(`/makercheckers/${id}?command=approve`, {}).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['maker-checker'] }),
  })
}

export function useRejectMakerChecker(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () =>
      apiClient.post(`/makercheckers/${id}?command=reject`, {}).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['maker-checker'] }),
  })
}

// ── Notifications ──────────────────────────────────────────────────────────────

export function useNotificationTemplates() {
  return useQuery({
    queryKey: ['notification-templates'],
    queryFn: () =>
      apiClient.get<{ data: NotificationTemplate[] }>('/notifications/templates').then(r => r.data),
  })
}

export function useCreateNotificationTemplate() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (req: CreateTemplateRequest) =>
      apiClient
        .post<{ data: NotificationTemplate }>('/notifications/templates', req)
        .then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['notification-templates'] }),
  })
}

export function useUpdateNotificationTemplate(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (req: CreateTemplateRequest) =>
      apiClient
        .put<{ data: NotificationTemplate }>(`/notifications/templates/${id}`, req)
        .then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['notification-templates'] }),
  })
}

export function useDeactivateNotificationTemplate(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => apiClient.delete(`/notifications/templates/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['notification-templates'] }),
  })
}

export function useSendTestNotification() {
  return useMutation({
    mutationFn: ({ templateId, recipientRef }: { templateId: string; recipientRef: string }) =>
      apiClient
        .post<{ data: NotificationLog }>('/notifications/test', { templateId, recipientRef })
        .then(r => r.data),
  })
}

export function useNotificationHistory(params?: Record<string, string>) {
  return useQuery({
    queryKey: ['notification-history', params],
    queryFn: () =>
      apiClient
        .get<{ data: NotificationLog[] }>('/notifications/history', { params })
        .then(r => r.data),
  })
}

// ── TPP ────────────────────────────────────────────────────────────────────────

export function useTpps() {
  return useQuery({
    queryKey: ['tpps'],
    queryFn: () =>
      apiClient.get<{ data: TppRegistration[] }>('/openbanking/tpp').then(r => r.data),
  })
}

export function useRegisterTpp() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (req: RegisterTppRequest) =>
      apiClient
        .post<{ data: TppRegistration }>('/openbanking/tpp', req)
        .then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['tpps'] }),
  })
}

export function useActivateTpp(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () =>
      apiClient.post(`/openbanking/tpp/${id}?command=activate`, {}).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['tpps'] }),
  })
}

export function useRevokeTpp(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () =>
      apiClient.post(`/openbanking/tpp/${id}?command=revoke`, {}).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['tpps'] }),
  })
}
