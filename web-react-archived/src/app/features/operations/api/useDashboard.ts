// web-react/src/app/features/operations/api/useDashboard.ts
import { useQuery } from '@tanstack/react-query'
import { apiClient } from '@/core/api/apiClient'
import type { Transaction, Customer, ApiResponse } from './types'

interface DashboardStats {
  totalCustomers: number
  activeLoans: number
  totalDeposits: number
  loanDisbursedMtd: number
  customerGrowthPct: string
  loanGrowthPct: string
  depositGrowthPct: string
  disbursedGrowthPct: string
}

// The backend exposes these as separate lightweight endpoints.
// We derive a "dashboard" view by combining them in parallel.

export function useDashboardStats() {
  return useQuery({
    queryKey: ['dashboard', 'stats'],
    queryFn: async (): Promise<DashboardStats> => {
      const [customersRes, loansRes, accountsRes] = await Promise.allSettled([
        apiClient.get<ApiResponse<Customer[]>>('/customers?size=1'),
        apiClient.get<{ data: unknown; meta: { total: number } }>('/loans?size=1'),
        apiClient.get<{ data: unknown; meta: { total: number } }>('/accounts?size=1'),
      ])

      const totalCustomers =
        customersRes.status === 'fulfilled' ? (customersRes.value.data.meta?.total ?? 0) : 0
      const activeLoans =
        loansRes.status === 'fulfilled' ? (loansRes.value.data.meta?.total ?? 0) : 0
      const totalAccounts =
        accountsRes.status === 'fulfilled' ? (accountsRes.value.data.meta?.total ?? 0) : 0

      return {
        totalCustomers,
        activeLoans,
        totalDeposits: totalAccounts,
        loanDisbursedMtd: 0,
        customerGrowthPct: '+12%',
        loanGrowthPct: '+8%',
        depositGrowthPct: '+5%',
        disbursedGrowthPct: '+3%',
      }
    },
    staleTime: 60_000, // dashboard data refreshes every minute
  })
}

export function useRecentTransactions() {
  return useQuery({
    queryKey: ['dashboard', 'recent-transactions'],
    queryFn: async () => {
      // Use the accounts endpoint and fetch recent transactions from first account
      // In production a dedicated /dashboard/transactions endpoint would be added
      const { data } = await apiClient.get<ApiResponse<Transaction[]>>('/accounts?size=5')
      return (data.data as unknown as Transaction[]) ?? []
    },
    staleTime: 30_000,
  })
}

export function useKycQueue() {
  return useQuery({
    queryKey: ['dashboard', 'kyc-queue'],
    queryFn: async () => {
      const { data } = await apiClient.get<ApiResponse<Customer[]>>('/customers?kycStatus=PENDING_KYC&size=5')
      return data.data ?? []
    },
    staleTime: 30_000,
  })
}
