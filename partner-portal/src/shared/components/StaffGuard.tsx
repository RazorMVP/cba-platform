import { Navigate } from 'react-router-dom'
import { useAuth } from '../../app/context/AuthContext'
import type { ReactNode } from 'react'

export function StaffGuard({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  if (!user || user.role !== 'ADMIN') return <Navigate to="/dashboard" replace />
  return <>{children}</>
}
