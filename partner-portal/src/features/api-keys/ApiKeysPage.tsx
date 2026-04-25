import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../app/api/apiClient'
import { useAuth } from '../../app/context/AuthContext'
import { Plus, Copy, Trash2, Key, CheckCheck } from 'lucide-react'

interface ApiKey {
  id: string
  name: string
  keyPrefix: string
  scopes: string[]
  tier: string
  lastUsedAt: string | null
  createdAt: string
  active: boolean
}

const ALL_SCOPES = ['accounts:read', 'transactions:read', 'payments:write', 'cards:read', 'cards:write', 'webhooks:manage']

export default function ApiKeysPage() {
  const { user } = useAuth()
  const qc = useQueryClient()
  const [showModal, setShowModal] = useState(false)
  const [newKey, setNewKey] = useState<string | null>(null)
  const [keyName, setKeyName] = useState('')
  const [scopes, setScopes] = useState<string[]>(['accounts:read', 'transactions:read'])
  const [copied, setCopied] = useState(false)

  const { data: keys = [], isLoading } = useQuery<ApiKey[]>({
    queryKey: ['api-keys', user?.organizationId],
    queryFn: () => apiClient.get<{ data: ApiKey[] }>(`/partners/${user?.organizationId}/api-keys`).then(r => r.data.data),
    enabled: !!user?.organizationId,
  })

  const issue = useMutation({
    mutationFn: () => apiClient.post<{ data: { key: string } }>(`/partners/${user?.organizationId}/api-keys`, { name: keyName, scopes }),
    onSuccess: (res) => { setNewKey(res.data.data.key); qc.invalidateQueries({ queryKey: ['api-keys'] }) },
    onError: (err: unknown) => console.error('Failed to issue API key', err),
  })

  const revoke = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/partners/${user?.organizationId}/api-keys/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['api-keys'] }),
    onError: (err: unknown) => console.error('Failed to revoke API key', err),
  })

  const copy = (text: string) => { navigator.clipboard.writeText(text); setCopied(true); setTimeout(() => setCopied(false), 2000) }

  const toggleScope = (s: string) => setScopes(prev => prev.includes(s) ? prev.filter(x => x !== s) : [...prev, s])

  return (
    <div className="p-8">
      <div className="flex items-center justify-between mb-8">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">API Keys</h1>
          <p className="text-gray-500 text-sm mt-1">Issue and manage your sandbox API keys</p>
        </div>
        <button onClick={() => { setShowModal(true); setNewKey(null); setKeyName(''); setScopes(['accounts:read']) }} className="flex items-center gap-2 px-4 py-2 rounded-lg text-white text-sm font-medium" style={{ background: '#1e2833' }}>
          <Plus size={15} /> Issue New Key
        </button>
      </div>

      {/* One-time reveal */}
      {newKey && (
        <div className="bg-green-50 border border-green-300 rounded-xl p-5 mb-6">
          <p className="text-green-800 font-semibold text-sm mb-2 flex items-center gap-2"><Key size={14} /> Your new API key — copy it now. It will not be shown again.</p>
          <div className="flex items-center gap-3 bg-white border border-green-200 rounded-lg px-4 py-3">
            <code className="flex-1 text-sm font-mono text-gray-800 break-all">{newKey}</code>
            <button onClick={() => copy(newKey)} className="text-green-600 hover:text-green-800 flex-shrink-0">
              {copied ? <CheckCheck size={16} /> : <Copy size={16} />}
            </button>
          </div>
        </div>
      )}

      {/* Keys table */}
      <div className="bg-white rounded-xl border border-gray-100 overflow-hidden">
        {isLoading ? (
          <div className="p-10 text-center text-gray-400 text-sm">Loading keys…</div>
        ) : !keys.length ? (
          <div className="p-10 text-center">
            <Key size={32} className="text-gray-300 mx-auto mb-3" />
            <p className="text-gray-500 text-sm">No API keys yet. Issue your first key to start integrating.</p>
          </div>
        ) : (
          <table className="w-full text-sm">
            <thead className="bg-gray-50 border-b border-gray-100">
              <tr>
                {['Name', 'Prefix', 'Scopes', 'Tier', 'Last Used', 'Actions'].map(h => (
                  <th key={h} className="px-5 py-3 text-left text-xs font-medium text-gray-500 uppercase">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {keys.map(k => (
                <tr key={k.id} className="hover:bg-gray-50">
                  <td className="px-5 py-3 font-medium text-gray-900">{k.name}</td>
                  <td className="px-5 py-3 font-mono text-xs text-gray-600">{k.keyPrefix}•••</td>
                  <td className="px-5 py-3">
                    <div className="flex flex-wrap gap-1">
                      {k.scopes.map(s => <span key={s} className="bg-blue-50 text-blue-700 text-xs px-1.5 py-0.5 rounded">{s}</span>)}
                    </div>
                  </td>
                  <td className="px-5 py-3">
                    <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${k.tier === 'PRO' ? 'bg-purple-50 text-purple-700' : k.tier === 'ENTERPRISE' ? 'bg-amber-50 text-amber-700' : 'bg-gray-100 text-gray-600'}`}>{k.tier}</span>
                  </td>
                  <td className="px-5 py-3 text-gray-500 text-xs">{k.lastUsedAt ? new Date(k.lastUsedAt).toLocaleDateString() : 'Never'}</td>
                  <td className="px-5 py-3">
                    <button onClick={() => { if (confirm('Revoke this key? This cannot be undone.')) revoke.mutate(k.id) }} className="text-red-500 hover:text-red-700 p-1 rounded">
                      <Trash2 size={14} />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {/* Issue modal */}
      {showModal && !newKey && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-2xl w-full max-w-md shadow-2xl">
            <div className="px-6 py-5 border-b border-gray-100">
              <h2 className="font-bold text-gray-900">Issue API Key</h2>
            </div>
            <div className="px-6 py-5 space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1.5">Key name</label>
                <input value={keyName} onChange={e => setKeyName(e.target.value)} placeholder="e.g. Production Backend" className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">Scopes</label>
                <div className="space-y-2">
                  {ALL_SCOPES.map(s => (
                    <label key={s} className="flex items-center gap-2.5 cursor-pointer">
                      <input type="checkbox" checked={scopes.includes(s)} onChange={() => toggleScope(s)} className="rounded text-blue-600" />
                      <span className="text-sm text-gray-700 font-mono">{s}</span>
                    </label>
                  ))}
                </div>
              </div>
            </div>
            {issue.isError && (
              <p className="px-6 pb-2 text-xs text-red-600">Failed to issue key. Please try again.</p>
            )}
            <div className="px-6 py-4 bg-gray-50 rounded-b-2xl flex gap-3 justify-end">
              <button onClick={() => setShowModal(false)} className="px-4 py-2 text-sm text-gray-600 hover:text-gray-900">Cancel</button>
              <button onClick={() => issue.mutate()} disabled={!keyName || !scopes.length || issue.isPending} className="px-4 py-2 rounded-lg text-white text-sm font-medium disabled:opacity-60" style={{ background: '#1e2833' }}>
                {issue.isPending ? 'Issuing…' : 'Issue Key'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
