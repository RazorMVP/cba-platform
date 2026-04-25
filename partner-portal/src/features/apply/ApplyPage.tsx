import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { apiClient } from '../../app/api/apiClient'
import { useAuth } from '../../app/context/AuthContext'
import { CheckCircle2, AlertCircle, FileText } from 'lucide-react'

export default function ApplyPage() {
  const { user } = useAuth()
  const [form, setForm] = useState({ businessType: '', useCase: '', estimatedMonthlyCalls: '', website: '', technicalContact: '', complianceNotes: '' })
  const [submitted, setSubmitted] = useState(false)

  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) =>
    setForm(p => ({ ...p, [k]: e.target.value }))

  const apply = useMutation({
    mutationFn: () => apiClient.post(`/partners/${user?.organizationId}/applications`, form),
    onSuccess: () => setSubmitted(true),
    onError: (err: unknown) => console.error('Failed to submit application', err),
  })

  if (user?.environment === 'PRODUCTION') {
    return (
      <div className="p-8">
        <div className="bg-white rounded-xl border border-gray-100 p-12 text-center">
          <CheckCircle2 size={48} className="text-green-500 mx-auto mb-4" />
          <h2 className="text-xl font-bold text-gray-900 mb-2">You're Live!</h2>
          <p className="text-gray-500 text-sm">Your account already has Production access. Start making live API calls.</p>
        </div>
      </div>
    )
  }

  if (submitted) {
    return (
      <div className="p-8">
        <div className="bg-white rounded-xl border border-gray-100 p-12 text-center">
          <CheckCircle2 size={48} className="text-green-500 mx-auto mb-4" />
          <h2 className="text-xl font-bold text-gray-900 mb-2">Application Submitted</h2>
          <p className="text-gray-500 text-sm max-w-sm mx-auto">The NubBank partner team will review your application and respond within 3–5 business days.</p>
        </div>
      </div>
    )
  }

  return (
    <div className="p-8">
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-gray-900">Apply for Production Access</h1>
        <p className="text-gray-500 text-sm mt-1">Tell us about your integration and we'll review your application</p>
      </div>

      <div className="bg-amber-50 border border-amber-200 rounded-xl px-5 py-4 mb-8 flex items-start gap-3">
        <AlertCircle size={16} className="text-amber-600 mt-0.5 flex-shrink-0" />
        <div className="text-sm text-amber-800">
          <p className="font-medium mb-1">Before applying</p>
          <ul className="space-y-0.5 text-amber-700">
            <li>• Complete end-to-end testing in sandbox with realistic data volumes</li>
            <li>• Review the <a href="https://docs-site-five-dusky.vercel.app" target="_blank" rel="noreferrer" className="underline font-medium">NubBank Security &amp; Compliance guide</a></li>
            <li>• Confirm your webhook endpoint handles retries idempotently</li>
          </ul>
        </div>
      </div>

      <div className="bg-white rounded-xl border border-gray-100 overflow-hidden">
        <div className="px-6 py-5 border-b border-gray-100 flex items-center gap-2">
          <FileText size={16} className="text-gray-400" />
          <h2 className="font-semibold text-gray-900 text-sm">Production Application</h2>
        </div>
        <div className="px-6 py-6 space-y-5">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1.5">Business type</label>
              <select value={form.businessType} onChange={set('businessType')} required className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
                <option value="">Select…</option>
                {['Neobank', 'Payments Provider', 'Lending Platform', 'Insurance', 'Accounting Software', 'Other Fintech'].map(t => <option key={t}>{t}</option>)}
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1.5">Company website</label>
              <input value={form.website} onChange={set('website')} type="url" placeholder="https://yourcompany.com" className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
            </div>
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">Describe your use case</label>
            <textarea value={form.useCase} onChange={set('useCase')} rows={3} placeholder="What will you build with the NubBank API? Which endpoints will you use?" className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none" />
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1.5">Estimated monthly API calls</label>
              <select value={form.estimatedMonthlyCalls} onChange={set('estimatedMonthlyCalls')} className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
                <option value="">Select…</option>
                {['< 10,000', '10,000 – 100,000', '100,000 – 1M', '1M+'].map(t => <option key={t}>{t}</option>)}
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1.5">Technical contact email</label>
              <input value={form.technicalContact} onChange={set('technicalContact')} type="email" placeholder="cto@yourcompany.com" className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
            </div>
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">Compliance &amp; regulatory notes (optional)</label>
            <textarea value={form.complianceNotes} onChange={set('complianceNotes')} rows={2} placeholder="Any relevant regulatory licenses, FCA registration numbers, or compliance frameworks in place" className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none" />
          </div>
        </div>
        <div className="px-6 py-4 bg-gray-50 border-t border-gray-100 flex justify-end">
          {apply.isError && (
            <p className="text-xs text-red-600 mr-auto self-center">Submission failed. Please try again.</p>
          )}
          <button
            onClick={() => apply.mutate()}
            disabled={!form.businessType || !form.useCase || !form.estimatedMonthlyCalls || apply.isPending}
            className="px-6 py-2.5 rounded-lg text-white text-sm font-semibold disabled:opacity-60"
            style={{ background: '#1e2833' }}
          >
            {apply.isPending ? 'Submitting…' : 'Submit Application'}
          </button>
        </div>
      </div>
    </div>
  )
}
