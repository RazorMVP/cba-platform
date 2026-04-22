import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../app/api/apiClient'
import { Search, CheckCircle2, XCircle, Clock, ChevronRight } from 'lucide-react'

interface Partner {
  id: string
  organizationName: string
  email: string
  status: 'PENDING_REVIEW' | 'SANDBOX' | 'PRODUCTION' | 'SUSPENDED'
  tier: string
  applicationStatus?: string
  createdAt: string
  totalApiCalls: number
}

const STATUS_COLOR: Record<string, string> = {
  PENDING_REVIEW: 'bg-amber-50 text-amber-700',
  SANDBOX: 'bg-blue-50 text-blue-700',
  PRODUCTION: 'bg-green-50 text-green-700',
  SUSPENDED: 'bg-red-50 text-red-700',
}

export default function PartnerMgmtPage() {
  const qc = useQueryClient()
  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState<string>('ALL')
  const [selected, setSelected] = useState<Partner | null>(null)

  const { data: partners = [], isLoading } = useQuery<Partner[]>({
    queryKey: ['all-partners'],
    queryFn: () => apiClient.get<{ data: Partner[] }>('/partners').then(r => r.data.data),
  })

  const approve = useMutation({
    mutationFn: (id: string) => apiClient.post(`/partners/${id}/approve`),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['all-partners'] }); setSelected(null) },
  })

  const reject = useMutation({
    mutationFn: (id: string) => apiClient.post(`/partners/${id}/reject`),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['all-partners'] }); setSelected(null) },
  })

  const filtered = partners.filter(p => {
    const q = search.toLowerCase()
    const matchSearch = !q || p.organizationName.toLowerCase().includes(q) || p.email.toLowerCase().includes(q)
    const matchStatus = statusFilter === 'ALL' || p.status === statusFilter
    return matchSearch && matchStatus
  })

  const counts = {
    ALL: partners.length,
    PENDING_REVIEW: partners.filter(p => p.status === 'PENDING_REVIEW').length,
    PRODUCTION: partners.filter(p => p.status === 'PRODUCTION').length,
    SANDBOX: partners.filter(p => p.status === 'SANDBOX').length,
  }

  return (
    <div className="p-8">
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-gray-900">Partner Management</h1>
        <p className="text-gray-500 text-sm mt-1">Review and manage registered partner organizations</p>
      </div>

      {/* Summary cards */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
        {[
          { label: 'Total Partners', value: counts.ALL, icon: CheckCircle2, color: 'bg-blue-50 text-blue-600' },
          { label: 'Pending Review', value: counts.PENDING_REVIEW, icon: Clock, color: 'bg-amber-50 text-amber-600' },
          { label: 'Production', value: counts.PRODUCTION, icon: CheckCircle2, color: 'bg-green-50 text-green-600' },
          { label: 'Sandbox Only', value: counts.SANDBOX, icon: XCircle, color: 'bg-gray-100 text-gray-600' },
        ].map(card => (
          <div key={card.label} className="bg-white rounded-xl border border-gray-100 p-4">
            <div className="flex items-center gap-3">
              <div className={`p-2 rounded-lg ${card.color}`}><card.icon size={16} /></div>
              <div><p className="text-lg font-bold text-gray-900">{card.value}</p><p className="text-gray-500 text-xs">{card.label}</p></div>
            </div>
          </div>
        ))}
      </div>

      {/* Filters */}
      <div className="flex items-center gap-3 mb-5 flex-wrap">
        <div className="relative flex-1 min-w-48 max-w-xs">
          <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
          <input value={search} onChange={e => setSearch(e.target.value)} placeholder="Search partners…" className="w-full pl-8 pr-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
        </div>
        <div className="flex rounded-lg border border-gray-200 overflow-hidden">
          {['ALL', 'PENDING_REVIEW', 'SANDBOX', 'PRODUCTION', 'SUSPENDED'].map(s => (
            <button key={s} onClick={() => setStatusFilter(s)} className={`px-3 py-1.5 text-xs font-medium transition-colors ${statusFilter === s ? 'bg-gray-900 text-white' : 'bg-white text-gray-600 hover:bg-gray-50'}`}>{s.replace('_', ' ')}</button>
          ))}
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* List */}
        <div className="lg:col-span-2">
          <div className="bg-white rounded-xl border border-gray-100 overflow-hidden">
            {isLoading ? (
              <div className="p-10 text-center text-gray-400 text-sm">Loading partners…</div>
            ) : !filtered.length ? (
              <div className="p-10 text-center text-gray-400 text-sm">No partners match the current filters.</div>
            ) : (
              <table className="w-full text-sm">
                <thead className="bg-gray-50 border-b"><tr>{['Organization', 'Status', 'Tier', 'API Calls', ''].map(h => <th key={h} className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">{h}</th>)}</tr></thead>
                <tbody className="divide-y divide-gray-100">
                  {filtered.map(p => (
                    <tr key={p.id} onClick={() => setSelected(p)} className={`cursor-pointer transition-colors ${selected?.id === p.id ? 'bg-blue-50' : 'hover:bg-gray-50'}`}>
                      <td className="px-4 py-3">
                        <p className="font-medium text-gray-900">{p.organizationName}</p>
                        <p className="text-gray-400 text-xs">{p.email}</p>
                      </td>
                      <td className="px-4 py-3"><span className={`text-xs px-2 py-0.5 rounded-full font-medium ${STATUS_COLOR[p.status] ?? 'bg-gray-100 text-gray-600'}`}>{p.status}</span></td>
                      <td className="px-4 py-3 text-gray-600 text-xs">{p.tier}</td>
                      <td className="px-4 py-3 text-gray-700 tabular-nums text-xs">{p.totalApiCalls.toLocaleString()}</td>
                      <td className="px-4 py-3"><ChevronRight size={14} className="text-gray-400" /></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        </div>

        {/* Detail */}
        <div className="lg:col-span-1">
          {!selected ? (
            <div className="bg-white rounded-xl border border-gray-100 p-10 text-center text-gray-400 text-sm">Select a partner to view details</div>
          ) : (
            <div className="bg-white rounded-xl border border-gray-100 overflow-hidden">
              <div className="px-5 py-4 border-b bg-gray-50">
                <p className="font-semibold text-gray-900">{selected.organizationName}</p>
                <p className="text-gray-400 text-xs mt-0.5">{selected.email}</p>
              </div>
              <div className="px-5 py-4 space-y-3">
                {[['Status', selected.status], ['Tier', selected.tier], ['Total API Calls', selected.totalApiCalls.toLocaleString()], ['Registered', new Date(selected.createdAt).toLocaleDateString()]].map(([l, v]) => (
                  <div key={l as string} className="flex justify-between text-sm">
                    <span className="text-gray-500">{l}</span>
                    <span className="font-medium text-gray-900">{v as string}</span>
                  </div>
                ))}
              </div>
              {selected.status === 'PENDING_REVIEW' && (
                <div className="px-5 py-4 border-t flex gap-2">
                  <button onClick={() => approve.mutate(selected.id)} disabled={approve.isPending} className="flex-1 py-2 rounded-lg text-white text-sm font-medium bg-green-600 hover:bg-green-700 disabled:opacity-60">
                    {approve.isPending ? 'Approving…' : 'Approve'}
                  </button>
                  <button onClick={() => reject.mutate(selected.id)} disabled={reject.isPending} className="flex-1 py-2 rounded-lg text-sm font-medium border border-red-300 text-red-600 hover:bg-red-50 disabled:opacity-60">
                    {reject.isPending ? 'Rejecting…' : 'Reject'}
                  </button>
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
