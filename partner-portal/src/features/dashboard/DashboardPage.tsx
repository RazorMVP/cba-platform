import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../../app/api/apiClient'
import { useAuth } from '../../app/context/AuthContext'
import { TrendingUp, Activity, AlertTriangle, CheckCircle2, Key, Webhook } from 'lucide-react'

interface UsageSummary {
  totalRequests: number
  successRequests: number
  failedRequests: number
  webhookDeliveryRate: number
  dailyCalls: Array<{ date: string; count: number }>
  topEndpoints: Array<{ path: string; method: string; count: number }>
}

function KpiCard({ label, value, sub, icon: Icon, color }: { label: string; value: string; sub?: string; icon: React.ComponentType<{ size?: number; className?: string }>; color: string }) {
  return (
    <div className="bg-white rounded-xl p-5 border border-gray-100">
      <div className="flex items-start justify-between">
        <div>
          <p className="text-gray-500 text-xs font-medium uppercase tracking-wide">{label}</p>
          <p className="text-2xl font-bold text-gray-900 mt-1">{value}</p>
          {sub && <p className="text-gray-400 text-xs mt-0.5">{sub}</p>}
        </div>
        <div className={`p-2.5 rounded-lg ${color}`}>
          <Icon size={18} />
        </div>
      </div>
    </div>
  )
}

export default function DashboardPage() {
  const { user } = useAuth()
  const { data, isLoading } = useQuery<UsageSummary>({
    queryKey: ['partner-usage', user?.organizationId],
    queryFn: () => apiClient.get<{ data: UsageSummary }>(`/partners/${user?.organizationId}/usage`).then(r => r.data.data),
    enabled: !!user?.organizationId,
    placeholderData: { totalRequests: 0, successRequests: 0, failedRequests: 0, webhookDeliveryRate: 0, dailyCalls: [], topEndpoints: [] },
  })

  const successRate = data && data.totalRequests > 0
    ? Math.round((data.successRequests / data.totalRequests) * 100)
    : 0

  return (
    <div className="p-8">
      {/* Header */}
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-gray-900">Dashboard</h1>
        <p className="text-gray-500 text-sm mt-1">
          Welcome back, <span className="font-medium text-gray-700">{user?.organizationName}</span>
        </p>
      </div>

      {/* Env banner for sandbox */}
      {user?.environment === 'SANDBOX' && (
        <div className="bg-amber-50 border border-amber-200 rounded-xl px-5 py-3 mb-6 flex items-center justify-between">
          <div className="flex items-center gap-2 text-amber-800 text-sm">
            <AlertTriangle size={15} />
            <span className="font-medium">Sandbox Mode</span> — All transactions use test data. No real money moves.
          </div>
          <a href="/apply" className="text-sm font-medium text-amber-700 hover:text-amber-900 underline">
            Apply for Production →
          </a>
        </div>
      )}

      {/* KPIs */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
        <KpiCard label="Total API Calls" value={isLoading ? '—' : data!.totalRequests.toLocaleString()} icon={Activity} color="bg-blue-50 text-blue-600" />
        <KpiCard label="Success Rate" value={isLoading ? '—' : `${successRate}%`} sub={`${data?.successRequests.toLocaleString() ?? 0} successful`} icon={CheckCircle2} color="bg-green-50 text-green-600" />
        <KpiCard label="Failed Calls" value={isLoading ? '—' : data!.failedRequests.toLocaleString()} icon={AlertTriangle} color="bg-red-50 text-red-600" />
        <KpiCard label="Webhook Delivery" value={isLoading ? '—' : `${data!.webhookDeliveryRate}%`} icon={TrendingUp} color="bg-purple-50 text-purple-600" />
      </div>

      {/* Quick links */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-8">
        <a href="/api-keys" className="bg-white rounded-xl p-5 border border-gray-100 hover:border-blue-300 transition-colors group">
          <div className="flex items-center gap-3">
            <div className="p-2.5 bg-blue-50 rounded-lg text-blue-600 group-hover:bg-blue-100 transition-colors">
              <Key size={18} />
            </div>
            <div>
              <p className="font-semibold text-gray-900 text-sm">Manage API Keys</p>
              <p className="text-gray-400 text-xs">Issue, view, and revoke your keys</p>
            </div>
          </div>
        </a>
        <a href="/webhooks" className="bg-white rounded-xl p-5 border border-gray-100 hover:border-purple-300 transition-colors group">
          <div className="flex items-center gap-3">
            <div className="p-2.5 bg-purple-50 rounded-lg text-purple-600 group-hover:bg-purple-100 transition-colors">
              <Webhook size={18} />
            </div>
            <div>
              <p className="font-semibold text-gray-900 text-sm">Configure Webhooks</p>
              <p className="text-gray-400 text-xs">Register endpoints and view delivery logs</p>
            </div>
          </div>
        </a>
      </div>

      {/* Top endpoints */}
      <div className="bg-white rounded-xl border border-gray-100 overflow-hidden">
        <div className="px-5 py-4 border-b border-gray-100">
          <h2 className="font-semibold text-gray-900 text-sm">Top Endpoints (Last 30 Days)</h2>
        </div>
        {!data?.topEndpoints.length ? (
          <div className="px-5 py-10 text-center text-gray-400 text-sm">No API calls recorded yet. Start by reading the <a href="https://docs-site-five-dusky.vercel.app/getting-started" className="text-blue-500 underline">Getting Started guide</a>.</div>
        ) : (
          <table className="w-full text-sm">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-5 py-3 text-left text-xs font-medium text-gray-500 uppercase">Endpoint</th>
                <th className="px-5 py-3 text-right text-xs font-medium text-gray-500 uppercase">Calls</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {data.topEndpoints.map((ep, i) => (
                <tr key={i} className="hover:bg-gray-50">
                  <td className="px-5 py-3 font-mono text-xs">
                    <span className="bg-blue-50 text-blue-700 px-1.5 py-0.5 rounded mr-2">{ep.method}</span>
                    {ep.path}
                  </td>
                  <td className="px-5 py-3 text-right font-semibold tabular-nums">{ep.count.toLocaleString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  )
}
