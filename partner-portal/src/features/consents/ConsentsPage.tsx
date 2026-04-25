import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../app/api/apiClient'
import { useAuth } from '../../app/context/AuthContext'
import { Shield, Trash2 } from 'lucide-react'

interface Consent { id: string; consentType: 'AISP' | 'PISP' | 'CBPII'; status: string; permissions: string[]; expirationDateTime: string; createdDateTime: string; transactionFromDateTime?: string; transactionToDateTime?: string }

const STATUS_COLOR: Record<string, string> = {
  AUTHORISED: 'bg-green-50 text-green-700',
  AWAITING_AUTHORISATION: 'bg-amber-50 text-amber-700',
  REVOKED: 'bg-red-50 text-red-700',
  EXPIRED: 'bg-gray-100 text-gray-500',
}

const TYPE_COLOR: Record<string, string> = {
  AISP: 'bg-blue-50 text-blue-700',
  PISP: 'bg-purple-50 text-purple-700',
  CBPII: 'bg-teal-50 text-teal-700',
}

export default function ConsentsPage() {
  const { user } = useAuth()
  const qc = useQueryClient()
  const [filter, setFilter] = useState<'ALL' | 'AISP' | 'PISP' | 'CBPII'>('ALL')
  const [statusFilter, setStatusFilter] = useState<string>('ALL')

  const { data: consents = [], isLoading } = useQuery<Consent[]>({
    queryKey: ['consents', user?.organizationId],
    queryFn: () => apiClient.get<{ data: Consent[] }>(`/partners/${user?.organizationId}/consents`).then(r => r.data.data),
    enabled: !!user,
  })

  const revoke = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/open-banking/v3.1/account-access-consents/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['consents'] }),
    onError: (err: unknown) => console.error('Failed to revoke consent', err),
  })

  const filtered = consents.filter(c =>
    (filter === 'ALL' || c.consentType === filter) &&
    (statusFilter === 'ALL' || c.status === statusFilter)
  )

  return (
    <div className="p-8">
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-gray-900">Active Consents</h1>
        <p className="text-gray-500 text-sm mt-1">Open Banking consents granted by your customers via your application</p>
      </div>

      {/* Filters */}
      <div className="flex items-center gap-3 mb-6 flex-wrap">
        <div className="flex rounded-lg border border-gray-200 overflow-hidden">
          {(['ALL', 'AISP', 'PISP', 'CBPII'] as const).map(t => (
            <button key={t} onClick={() => setFilter(t)} className={`px-3 py-1.5 text-xs font-medium transition-colors ${filter === t ? 'bg-gray-900 text-white' : 'bg-white text-gray-600 hover:bg-gray-50'}`}>{t}</button>
          ))}
        </div>
        <select value={statusFilter} onChange={e => setStatusFilter(e.target.value)} className="text-xs border border-gray-200 rounded-lg px-3 py-1.5 text-gray-600 focus:outline-none focus:ring-2 focus:ring-blue-500">
          {['ALL', 'AUTHORISED', 'AWAITING_AUTHORISATION', 'REVOKED', 'EXPIRED'].map(s => <option key={s}>{s}</option>)}
        </select>
      </div>

      {isLoading ? (
        <div className="text-center py-12 text-gray-400">Loading consents…</div>
      ) : !filtered.length ? (
        <div className="bg-white rounded-xl border border-gray-100 p-12 text-center">
          <Shield size={36} className="text-gray-300 mx-auto mb-3" />
          <p className="text-gray-500 text-sm">No consents match the selected filters.</p>
        </div>
      ) : (
        <div className="space-y-3">
          {filtered.map(c => (
            <div key={c.id} className="bg-white rounded-xl border border-gray-100 p-5">
              <div className="flex items-start justify-between gap-4">
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 mb-2 flex-wrap">
                    <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${TYPE_COLOR[c.consentType]}`}>{c.consentType}</span>
                    <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${STATUS_COLOR[c.status] ?? 'bg-gray-100 text-gray-600'}`}>{c.status}</span>
                    <span className="text-xs text-gray-400 font-mono">{c.id}</span>
                  </div>
                  <div className="flex flex-wrap gap-1 mb-3">
                    {c.permissions.map(p => <span key={p} className="text-xs bg-gray-100 text-gray-600 px-1.5 py-0.5 rounded">{p}</span>)}
                  </div>
                  <div className="flex gap-6 text-xs text-gray-500">
                    <span>Created: {new Date(c.createdDateTime).toLocaleDateString()}</span>
                    <span>Expires: {new Date(c.expirationDateTime).toLocaleDateString()}</span>
                  </div>
                </div>
                {c.status === 'AUTHORISED' && (
                  <button onClick={() => { if (confirm('Revoke this consent?')) revoke.mutate(c.id) }} className="flex items-center gap-1.5 px-3 py-1.5 text-xs text-red-600 border border-red-200 rounded-lg hover:bg-red-50 flex-shrink-0">
                    <Trash2 size={12} /> Revoke
                  </button>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
