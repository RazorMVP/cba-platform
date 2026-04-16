// web-react/src/app/features/operations/api/useLoans.ts
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/core/api/apiClient'
import type { Loan, RepaymentScheduleItem, ApiResponse } from './types'

export function useLoans(params?: { page?: number; size?: number; status?: string }) {
  return useQuery({
    queryKey: ['loans', params],
    queryFn: async () => {
      const { data } = await apiClient.get<ApiResponse<Loan[]>>('/loans', { params })
      return data
    },
  })
}

export function useLoan(id: string) {
  return useQuery({
    queryKey: ['loans', id],
    queryFn: async () => {
      const { data } = await apiClient.get<ApiResponse<Loan>>(`/loans/${id}`)
      return data.data
    },
    enabled: !!id && id !== 'new',
  })
}

export function useLoanSchedule(loanId: string) {
  return useQuery({
    queryKey: ['loans', loanId, 'schedule'],
    queryFn: async () => {
      const { data } = await apiClient.get<ApiResponse<RepaymentScheduleItem[]>>(`/loans/${loanId}/repaymentschedule`)
      return data
    },
    enabled: !!loanId && loanId !== 'new',
  })
}

export function useCreateLoan() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: Partial<Loan>) => apiClient.post<ApiResponse<Loan>>('/loans', body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['loans'] }),
  })
}

export function useLoanCommand(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ command, body }: { command: string; body?: Record<string, unknown> }) =>
      apiClient.post(`/loans/${id}?command=${command}`, body ?? {}),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['loans', id] }),
  })
}
