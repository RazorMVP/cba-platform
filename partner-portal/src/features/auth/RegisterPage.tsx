import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { apiClient } from '../../app/api/apiClient'
import { AlertCircle, CheckCircle2 } from 'lucide-react'

export default function RegisterPage() {
  const navigate = useNavigate()
  const [form, setForm] = useState({ organizationName: '', email: '', password: '', confirm: '' })
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const [done, setDone] = useState(false)

  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm(prev => ({ ...prev, [k]: e.target.value }))

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    if (form.password !== form.confirm) { setError('Passwords do not match.'); return }
    setError(''); setLoading(true)
    try {
      await apiClient.post('/partners/register', {
        organizationName: form.organizationName,
        email: form.email,
        password: form.password,
      })
      setDone(true)
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { errors?: Array<{ message: string }> } } })?.response?.data?.errors?.[0]?.message
      setError(msg ?? 'Registration failed. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  if (done) {
    return (
      <div className="min-h-screen flex items-center justify-center" style={{ background: '#040609' }}>
        <div className="w-full max-w-md px-4">
          <div className="bg-white rounded-2xl p-8 shadow-2xl text-center">
            <CheckCircle2 size={48} className="text-green-500 mx-auto mb-4" />
            <h2 className="text-xl font-bold text-gray-900 mb-2">Account Created!</h2>
            <p className="text-gray-500 text-sm mb-6">
              Your sandbox is ready. You can start making API calls immediately with your test credentials.
            </p>
            <button onClick={() => navigate('/login')} className="w-full py-2.5 rounded-lg text-white text-sm font-semibold" style={{ background: '#1e2833' }}>
              Sign In Now
            </button>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="min-h-screen flex items-center justify-center" style={{ background: '#040609' }}>
      <div className="w-full max-w-md px-4">
        <div className="text-center mb-8">
          <h1 className="text-white text-2xl font-bold">Create your account</h1>
          <p className="text-white/50 text-sm mt-1">Get sandbox access instantly — no credit card required</p>
        </div>

        <div className="bg-white rounded-2xl p-8 shadow-2xl">
          {error && (
            <div className="flex items-center gap-2 bg-red-50 border border-red-200 text-red-700 rounded-lg px-4 py-3 mb-5 text-sm">
              <AlertCircle size={15} />{error}
            </div>
          )}
          <form onSubmit={handleSubmit} className="space-y-4">
            {[
              { label: 'Company / Organization name', key: 'organizationName' as const, type: 'text', placeholder: 'Acme Fintech Ltd' },
              { label: 'Work email', key: 'email' as const, type: 'email', placeholder: 'dev@acme.io' },
              { label: 'Password', key: 'password' as const, type: 'password', placeholder: '8+ characters' },
              { label: 'Confirm password', key: 'confirm' as const, type: 'password', placeholder: 'Repeat password' },
            ].map(field => (
              <div key={field.key}>
                <label className="block text-sm font-medium text-gray-700 mb-1.5">{field.label}</label>
                <input
                  type={field.type} required value={form[field.key]} onChange={set(field.key)}
                  placeholder={field.placeholder} minLength={field.key === 'password' || field.key === 'confirm' ? 8 : 1}
                  className="w-full px-3 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                />
              </div>
            ))}
            <button type="submit" disabled={loading} className="w-full py-2.5 rounded-lg text-white text-sm font-semibold mt-2 transition-opacity disabled:opacity-60" style={{ background: '#1e2833' }}>
              {loading ? 'Creating account…' : 'Create Free Account'}
            </button>
          </form>
          <p className="text-center text-sm text-gray-500 mt-5">
            Already have an account? <Link to="/login" className="text-blue-600 font-medium hover:underline">Sign in</Link>
          </p>
        </div>
      </div>
    </div>
  )
}
