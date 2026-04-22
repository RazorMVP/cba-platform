import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { apiClient } from '../../app/api/apiClient'
import { useAuth } from '../../app/context/AuthContext'
import { CheckCircle2, User, Building2, Lock } from 'lucide-react'

export default function SettingsPage() {
  const { user } = useAuth()
  const [tab, setTab] = useState<'profile' | 'org' | 'security'>('profile')
  const [profileForm, setProfileForm] = useState({ email: user?.email ?? '' })
  const [orgForm, setOrgForm] = useState({ organizationName: user?.organizationName ?? '', website: '' })
  const [pwForm, setPwForm] = useState({ current: '', next: '', confirm: '' })
  const [saved, setSaved] = useState(false)

  const showSaved = () => { setSaved(true); setTimeout(() => setSaved(false), 2500) }

  const saveProfile = useMutation({
    mutationFn: () => apiClient.put(`/partners/users/${user?.id}`, profileForm),
    onSuccess: showSaved,
  })

  const saveOrg = useMutation({
    mutationFn: () => apiClient.put(`/partners/${user?.organizationId}`, orgForm),
    onSuccess: showSaved,
  })

  const changePassword = useMutation({
    mutationFn: () => apiClient.post(`/partners/users/${user?.id}/change-password`, { currentPassword: pwForm.current, newPassword: pwForm.next }),
    onSuccess: () => { showSaved(); setPwForm({ current: '', next: '', confirm: '' }) },
  })

  const tabs = [
    { id: 'profile' as const, label: 'Profile', icon: User },
    { id: 'org' as const, label: 'Organization', icon: Building2 },
    { id: 'security' as const, label: 'Security', icon: Lock },
  ]

  return (
    <div className="p-8">
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-gray-900">Settings</h1>
        <p className="text-gray-500 text-sm mt-1">Manage your profile and organization details</p>
      </div>

      {saved && (
        <div className="flex items-center gap-2 bg-green-50 border border-green-200 text-green-700 rounded-xl px-4 py-3 mb-6 text-sm">
          <CheckCircle2 size={15} /> Changes saved successfully.
        </div>
      )}

      <div className="flex gap-6">
        {/* Tab nav */}
        <div className="w-48 flex-shrink-0">
          <nav className="space-y-1">
            {tabs.map(t => (
              <button key={t.id} onClick={() => setTab(t.id)} className={`w-full flex items-center gap-2.5 px-3 py-2.5 rounded-lg text-sm transition-colors ${tab === t.id ? 'bg-gray-900 text-white font-medium' : 'text-gray-600 hover:bg-gray-100'}`}>
                <t.icon size={15} /> {t.label}
              </button>
            ))}
          </nav>
        </div>

        {/* Content */}
        <div className="flex-1">
          {tab === 'profile' && (
            <div className="bg-white rounded-xl border border-gray-100 p-6">
              <h2 className="font-semibold text-gray-900 mb-5">Profile Information</h2>
              <div className="space-y-4 max-w-md">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1.5">Email address</label>
                  <input type="email" value={profileForm.email} onChange={e => setProfileForm(p => ({ ...p, email: e.target.value }))} className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1.5">Role</label>
                  <input readOnly value={user?.role ?? ''} className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm bg-gray-50 text-gray-500" />
                </div>
                <button onClick={() => saveProfile.mutate()} disabled={saveProfile.isPending} className="px-5 py-2 rounded-lg text-white text-sm font-medium disabled:opacity-60" style={{ background: '#1e2833' }}>
                  {saveProfile.isPending ? 'Saving…' : 'Save Profile'}
                </button>
              </div>
            </div>
          )}

          {tab === 'org' && (
            <div className="bg-white rounded-xl border border-gray-100 p-6">
              <h2 className="font-semibold text-gray-900 mb-5">Organization Details</h2>
              <div className="space-y-4 max-w-md">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1.5">Organization name</label>
                  <input value={orgForm.organizationName} onChange={e => setOrgForm(p => ({ ...p, organizationName: e.target.value }))} className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1.5">Company website</label>
                  <input type="url" value={orgForm.website} onChange={e => setOrgForm(p => ({ ...p, website: e.target.value }))} placeholder="https://yourcompany.com" className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                </div>
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1.5">Environment</label>
                    <div className={`px-3 py-2 rounded-lg text-sm font-medium text-center ${user?.environment === 'PRODUCTION' ? 'bg-green-50 text-green-700' : 'bg-amber-50 text-amber-700'}`}>{user?.environment}</div>
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1.5">Tier</label>
                    <div className="px-3 py-2 rounded-lg text-sm font-medium text-center bg-gray-100 text-gray-700">{user?.tier ?? 'BASIC'}</div>
                  </div>
                </div>
                <button onClick={() => saveOrg.mutate()} disabled={saveOrg.isPending} className="px-5 py-2 rounded-lg text-white text-sm font-medium disabled:opacity-60" style={{ background: '#1e2833' }}>
                  {saveOrg.isPending ? 'Saving…' : 'Save Organization'}
                </button>
              </div>
            </div>
          )}

          {tab === 'security' && (
            <div className="bg-white rounded-xl border border-gray-100 p-6">
              <h2 className="font-semibold text-gray-900 mb-5">Change Password</h2>
              <div className="space-y-4 max-w-md">
                {[
                  { label: 'Current password', key: 'current' as const },
                  { label: 'New password', key: 'next' as const },
                  { label: 'Confirm new password', key: 'confirm' as const },
                ].map(f => (
                  <div key={f.key}>
                    <label className="block text-sm font-medium text-gray-700 mb-1.5">{f.label}</label>
                    <input type="password" value={pwForm[f.key]} onChange={e => setPwForm(p => ({ ...p, [f.key]: e.target.value }))} className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                  </div>
                ))}
                <button
                  onClick={() => changePassword.mutate()}
                  disabled={!pwForm.current || !pwForm.next || pwForm.next !== pwForm.confirm || changePassword.isPending}
                  className="px-5 py-2 rounded-lg text-white text-sm font-medium disabled:opacity-60"
                  style={{ background: '#1e2833' }}
                >
                  {changePassword.isPending ? 'Changing…' : 'Change Password'}
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
