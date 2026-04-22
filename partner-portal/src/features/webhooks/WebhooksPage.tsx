import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../app/api/apiClient'
import { useAuth } from '../../app/context/AuthContext'
import { Plus, Trash2, ChevronRight, CheckCircle2, XCircle, Clock } from 'lucide-react'

interface Webhook { id: string; name: string; callbackUrl: string; events: string[]; active: boolean; createdAt: string }
interface Delivery { id: string; deliveryUuid: string; eventType: string; httpStatus: number; status: 'PENDING' | 'DELIVERED' | 'FAILED'; attemptCount: number; lastAttemptAt: string }

const EVENTS = ['AUTHORIZATION.APPROVED', 'AUTHORIZATION.DECLINED', 'CARD.ISSUED', 'CARD.BLOCKED', 'CARD.ACTIVATED', 'DISPUTE.RAISED', 'DISPUTE.RESOLVED']

export default function WebhooksPage() {
  const { user } = useAuth()
  const qc = useQueryClient()
  const [showModal, setShowModal] = useState(false)
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [form, setForm] = useState({ name: '', callbackUrl: '', secret: '', events: [] as string[] })

  const { data: hooks = [] } = useQuery<Webhook[]>({
    queryKey: ['webhooks', user?.organizationId],
    queryFn: () => apiClient.get<{ data: Webhook[] }>(`/partners/${user?.organizationId}/webhooks`).then(r => r.data.data),
    enabled: !!user,
  })

  const { data: deliveries = [] } = useQuery<Delivery[]>({
    queryKey: ['webhook-deliveries', selectedId],
    queryFn: () => apiClient.get<{ data: Delivery[] }>(`/partners/${user?.organizationId}/webhooks/${selectedId}/deliveries`).then(r => r.data.data),
    enabled: !!selectedId,
  })

  const create = useMutation({
    mutationFn: () => apiClient.post(`/partners/${user?.organizationId}/webhooks`, form),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['webhooks'] }); setShowModal(false); setForm({ name: '', callbackUrl: '', secret: '', events: [] }) },
  })

  const remove = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/partners/${user?.organizationId}/webhooks/${id}`),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['webhooks'] }); if (selectedId === remove.variables) setSelectedId(null) },
  })

  const toggleEvent = (e: string) => setForm(p => ({ ...p, events: p.events.includes(e) ? p.events.filter(x => x !== e) : [...p.events, e] }))

  const statusIcon = (s: Delivery['status']) => s === 'DELIVERED' ? <CheckCircle2 size={14} className="text-green-500" /> : s === 'FAILED' ? <XCircle size={14} className="text-red-500" /> : <Clock size={14} className="text-amber-500" />

  return (
    <div className="p-8">
      <div className="flex items-center justify-between mb-8">
        <div><h1 className="text-2xl font-bold text-gray-900">Webhooks</h1><p className="text-gray-500 text-sm mt-1">Receive real-time event notifications</p></div>
        <button onClick={() => setShowModal(true)} className="flex items-center gap-2 px-4 py-2 rounded-lg text-white text-sm font-medium" style={{ background: '#1e2833' }}>
          <Plus size={15} /> Register Webhook
        </button>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Webhook list */}
        <div className="lg:col-span-1 space-y-3">
          {!hooks.length ? (
            <div className="bg-white rounded-xl border border-gray-100 p-8 text-center text-gray-400 text-sm">No webhooks registered yet.</div>
          ) : hooks.map(h => (
            <div key={h.id} onClick={() => setSelectedId(h.id)} className={`bg-white rounded-xl border p-4 cursor-pointer transition-colors ${selectedId === h.id ? 'border-blue-400 ring-1 ring-blue-200' : 'border-gray-100 hover:border-gray-300'}`}>
              <div className="flex items-start justify-between gap-2">
                <div className="min-w-0">
                  <p className="font-medium text-gray-900 text-sm">{h.name}</p>
                  <p className="text-gray-400 text-xs truncate mt-0.5">{h.callbackUrl}</p>
                  <div className="flex flex-wrap gap-1 mt-2">{h.events.slice(0, 2).map(e => <span key={e} className="text-xs bg-purple-50 text-purple-700 px-1.5 py-0.5 rounded">{e}</span>)}{h.events.length > 2 && <span className="text-xs text-gray-400">+{h.events.length - 2}</span>}</div>
                </div>
                <div className="flex items-center gap-1 flex-shrink-0">
                  <button onClick={(ev) => { ev.stopPropagation(); if (confirm('Delete this webhook?')) remove.mutate(h.id) }} className="text-red-400 hover:text-red-600 p-1"><Trash2 size={13} /></button>
                  <ChevronRight size={14} className="text-gray-400" />
                </div>
              </div>
            </div>
          ))}
        </div>

        {/* Delivery log */}
        <div className="lg:col-span-2">
          {!selectedId ? (
            <div className="bg-white rounded-xl border border-gray-100 p-12 text-center text-gray-400 text-sm flex flex-col items-center gap-2">
              Select a webhook to view delivery history
            </div>
          ) : (
            <div className="bg-white rounded-xl border border-gray-100 overflow-hidden">
              <div className="px-5 py-4 border-b border-gray-100"><p className="font-semibold text-gray-900 text-sm">Delivery Log (Last 100)</p></div>
              {!deliveries.length ? (
                <div className="p-10 text-center text-gray-400 text-sm">No deliveries yet.</div>
              ) : (
                <table className="w-full text-sm">
                  <thead className="bg-gray-50"><tr>{['Event', 'Status', 'HTTP', 'Attempts', 'Last Attempt'].map(h => <th key={h} className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">{h}</th>)}</tr></thead>
                  <tbody className="divide-y divide-gray-100">
                    {deliveries.map(d => (
                      <tr key={d.id} className="hover:bg-gray-50">
                        <td className="px-4 py-3 text-xs font-mono text-gray-700">{d.eventType}</td>
                        <td className="px-4 py-3"><div className="flex items-center gap-1">{statusIcon(d.status)}<span className="text-xs">{d.status}</span></div></td>
                        <td className="px-4 py-3"><span className={`text-xs font-mono px-1.5 py-0.5 rounded ${d.httpStatus >= 200 && d.httpStatus < 300 ? 'bg-green-50 text-green-700' : 'bg-red-50 text-red-700'}`}>{d.httpStatus}</span></td>
                        <td className="px-4 py-3 text-gray-600 tabular-nums">{d.attemptCount}</td>
                        <td className="px-4 py-3 text-gray-400 text-xs">{new Date(d.lastAttemptAt).toLocaleString()}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          )}
        </div>
      </div>

      {/* Create modal */}
      {showModal && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-2xl w-full max-w-md shadow-2xl">
            <div className="px-6 py-5 border-b"><h2 className="font-bold text-gray-900">Register Webhook</h2></div>
            <div className="px-6 py-5 space-y-4">
              {[{ label: 'Name', key: 'name' as const, placeholder: 'My Webhook' }, { label: 'Callback URL', key: 'callbackUrl' as const, placeholder: 'https://yourapp.com/webhook' }, { label: 'Signing Secret', key: 'secret' as const, placeholder: 'whsec_…' }].map(f => (
                <div key={f.key}>
                  <label className="block text-sm font-medium text-gray-700 mb-1.5">{f.label}</label>
                  <input value={form[f.key]} onChange={e => setForm(p => ({ ...p, [f.key]: e.target.value }))} placeholder={f.placeholder} className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                </div>
              ))}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">Events</label>
                <div className="space-y-1.5 max-h-40 overflow-y-auto">
                  {EVENTS.map(e => <label key={e} className="flex items-center gap-2 cursor-pointer text-sm text-gray-700"><input type="checkbox" checked={form.events.includes(e)} onChange={() => toggleEvent(e)} className="rounded text-blue-600" />{e}</label>)}
                </div>
              </div>
            </div>
            <div className="px-6 py-4 bg-gray-50 rounded-b-2xl flex gap-3 justify-end">
              <button onClick={() => setShowModal(false)} className="px-4 py-2 text-sm text-gray-600 hover:text-gray-900">Cancel</button>
              <button onClick={() => create.mutate()} disabled={!form.name || !form.callbackUrl || !form.events.length || create.isPending} className="px-4 py-2 rounded-lg text-white text-sm font-medium disabled:opacity-60" style={{ background: '#1e2833' }}>
                {create.isPending ? 'Registering…' : 'Register'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
