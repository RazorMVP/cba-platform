// web-react/src/app/layout/Shell.tsx
import { Outlet } from 'react-router-dom'
import Sidebar from './Sidebar'
import Topbar from './Topbar'

export default function Shell() {
  return (
    <div style={{ display: 'flex', minHeight: '100vh', background: 'var(--bg-app)' }}>
      <Sidebar />
      <div
        style={{
          marginLeft: 'var(--sidebar-width)',
          flex: 1,
          display: 'flex',
          flexDirection: 'column',
        }}
      >
        <Topbar />
        <main
          style={{
            marginTop: 'var(--topbar-height)',
            flex: 1,
            padding: '32px',
            overflowY: 'auto',
          }}
        >
          <Outlet />
        </main>
      </div>
    </div>
  )
}
