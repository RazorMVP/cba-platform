import { Outlet, NavLink } from 'react-router-dom'
import {
  LayoutDashboard, Key, Webhook, Shield, FlaskConical,
  FileText, Users, BarChart3, Settings, LogOut, ChevronRight
} from 'lucide-react'
import { useAuth } from '../../app/context/AuthContext'
import clsx from 'clsx'

interface NavItem {
  to: string
  label: string
  icon: React.ComponentType<{ size?: number; className?: string }>
  adminOnly?: boolean
}

const navItems: NavItem[] = [
  { to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
  { to: '/api-keys', label: 'API Keys', icon: Key },
  { to: '/webhooks', label: 'Webhooks', icon: Webhook },
  { to: '/consents', label: 'Consents', icon: Shield },
  { to: '/sandbox', label: 'Sandbox', icon: FlaskConical },
  { to: '/apply', label: 'Apply for Production', icon: FileText },
  { to: '/partner-management', label: 'Partner Management', icon: Users, adminOnly: true },
  { to: '/usage-analytics', label: 'Usage Analytics', icon: BarChart3, adminOnly: true },
  { to: '/settings', label: 'Settings', icon: Settings },
]

export function AppShell() {
  const { user, logout } = useAuth()

  const visibleItems = navItems.filter(item => !item.adminOnly || user?.role === 'ADMIN')

  return (
    <div className="flex h-screen overflow-hidden" style={{ background: '#040609' }}>
      {/* Sidebar */}
      <aside className="w-64 flex flex-col flex-shrink-0" style={{ background: '#0a1628' }}>
        {/* Logo */}
        <div className="flex items-center gap-3 px-6 py-5 border-b border-white/10">
          <div className="w-8 h-8 rounded-full overflow-hidden flex-shrink-0" style={{ background: '#1e2833' }}>
            <img src="/nubeero-logo.png" alt="NubBank" className="w-full h-full object-cover" onError={(e) => { (e.target as HTMLImageElement).style.display = 'none' }} />
          </div>
          <div>
            <div className="text-white font-semibold text-sm leading-tight">NubBank</div>
            <div className="text-white/50 text-xs">Partner Portal</div>
          </div>
        </div>

        {/* Env badge */}
        <div className="px-6 py-3">
          <span className={clsx(
            'inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium',
            user?.environment === 'PRODUCTION'
              ? 'bg-green-500/20 text-green-400'
              : 'bg-amber-500/20 text-amber-400'
          )}>
            <span className={clsx('w-1.5 h-1.5 rounded-full', user?.environment === 'PRODUCTION' ? 'bg-green-400' : 'bg-amber-400')} />
            {user?.environment ?? 'Sandbox'}
          </span>
        </div>

        {/* Nav */}
        <nav className="flex-1 overflow-y-auto px-3 py-2 space-y-0.5">
          {visibleItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) => clsx(
                'flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm transition-colors group',
                isActive
                  ? 'bg-white/10 text-white font-medium'
                  : 'text-white/60 hover:text-white hover:bg-white/5'
              )}
            >
              <item.icon size={16} />
              <span className="flex-1">{item.label}</span>
              <ChevronRight size={12} className="opacity-0 group-hover:opacity-60 transition-opacity" />
            </NavLink>
          ))}
        </nav>

        {/* User footer */}
        <div className="px-4 py-4 border-t border-white/10">
          <div className="flex items-center gap-3">
            <div className="w-8 h-8 rounded-full flex items-center justify-center text-white text-xs font-semibold flex-shrink-0" style={{ background: '#1e2833' }}>
              {user?.email.charAt(0).toUpperCase()}
            </div>
            <div className="flex-1 min-w-0">
              <div className="text-white text-xs font-medium truncate">{user?.organizationName}</div>
              <div className="text-white/40 text-xs truncate">{user?.email}</div>
            </div>
            <button
              onClick={logout}
              className="text-white/40 hover:text-white transition-colors p-1 rounded"
              title="Sign out"
            >
              <LogOut size={14} />
            </button>
          </div>
        </div>
      </aside>

      {/* Main content */}
      <main className="flex-1 overflow-y-auto bg-gray-50">
        <Outlet />
      </main>
    </div>
  )
}
