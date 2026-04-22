import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../../app/api/apiClient'
import { useState } from 'react'

interface PartnerUsage {
  organizationId: string
  organizationName: string
  totalCalls: number
  successCalls: number
  errorCalls: number
  topEndpoints: Array<{ path: string; count: number }>
  dailySeries: Array<{ date: string; count: number }>
}

export default function UsageAnalyticsPage() {
  const [days, setDays] = useState(30)

  const { data = [], isLoading } = useQuery<PartnerUsage[]>({
    queryKey: ['all-partner-usage', days],
    queryFn: () => apiClient.get<{ data: PartnerUsage[] }>(`/partners/usage?days=${days}`).then(r => r.data.data),
  })

  const totalCalls = data.reduce((sum, p) => sum + p.totalCalls, 0)
  const totalErrors = data.reduce((sum, p) => sum + p.errorCalls, 0)
  const avgSuccessRate = data.length
    ? Math.round(data.reduce((sum, p) => sum + (p.totalCalls > 0 ? (p.successCalls / p.totalCalls) * 100 : 0), 0) / data.length)
    : 0

  return (
    <div className="p-8">
      <div className="flex items-center justify-between mb-8">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Usage Analytics</h1>
          <p className="text-gray-500 text-sm mt-1">API usage across all registered partners</p>
        </div>
        <select value={days} onChange={e => setDays(Number(e.target.value))} className="text-sm border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500">
          {[7, 14, 30, 90].map(d => <option key={d} value={d}>Last {d} days</option>)}
        </select>
      </div>

      {/* Global KPIs */}
      <div className="grid grid-cols-3 gap-4 mb-8">
        {[
          { label: 'Total API Calls', value: totalCalls.toLocaleString(), color: 'text-blue-600 bg-blue-50' },
          { label: 'Avg Success Rate', value: `${avgSuccessRate}%`, color: 'text-green-600 bg-green-50' },
          { label: 'Total Errors', value: totalErrors.toLocaleString(), color: 'text-red-600 bg-red-50' },
        ].map(kpi => (
          <div key={kpi.label} className="bg-white rounded-xl border border-gray-100 p-5">
            <p className="text-gray-500 text-xs font-medium uppercase tracking-wide">{kpi.label}</p>
            <p className={`text-2xl font-bold mt-1 ${kpi.color.split(' ')[0]}`}>{kpi.value}</p>
          </div>
        ))}
      </div>

      {/* Per-partner table */}
      <div className="bg-white rounded-xl border border-gray-100 overflow-hidden">
        <div className="px-5 py-4 border-b border-gray-100">
          <h2 className="font-semibold text-gray-900 text-sm">Per-Partner Breakdown</h2>
        </div>
        {isLoading ? (
          <div className="p-10 text-center text-gray-400 text-sm">Loading usage data…</div>
        ) : !data.length ? (
          <div className="p-10 text-center text-gray-400 text-sm">No usage data for this period.</div>
        ) : (
          <table className="w-full text-sm">
            <thead className="bg-gray-50 border-b">
              <tr>{['Organization', 'Total Calls', 'Successful', 'Errors', 'Success Rate', 'Top Endpoint'].map(h => <th key={h} className="px-5 py-3 text-left text-xs font-medium text-gray-500 uppercase">{h}</th>)}</tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {data.sort((a, b) => b.totalCalls - a.totalCalls).map(p => {
                const rate = p.totalCalls > 0 ? Math.round((p.successCalls / p.totalCalls) * 100) : 0
                return (
                  <tr key={p.organizationId} className="hover:bg-gray-50">
                    <td className="px-5 py-3 font-medium text-gray-900">{p.organizationName}</td>
                    <td className="px-5 py-3 tabular-nums text-gray-700">{p.totalCalls.toLocaleString()}</td>
                    <td className="px-5 py-3 tabular-nums text-green-700">{p.successCalls.toLocaleString()}</td>
                    <td className="px-5 py-3 tabular-nums text-red-600">{p.errorCalls.toLocaleString()}</td>
                    <td className="px-5 py-3">
                      <div className="flex items-center gap-2">
                        <div className="flex-1 bg-gray-200 rounded-full h-1.5 max-w-20">
                          <div className={`h-1.5 rounded-full ${rate >= 95 ? 'bg-green-500' : rate >= 80 ? 'bg-amber-500' : 'bg-red-500'}`} style={{ width: `${rate}%` }} />
                        </div>
                        <span className="text-xs tabular-nums text-gray-700">{rate}%</span>
                      </div>
                    </td>
                    <td className="px-5 py-3 font-mono text-xs text-gray-500 truncate max-w-xs">{p.topEndpoints[0]?.path ?? '—'}</td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        )}
      </div>
    </div>
  )
}
