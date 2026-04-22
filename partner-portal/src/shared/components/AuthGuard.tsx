import { Navigate } from 'react-router-dom'
import { useAuth } from '../../app/context/AuthContext'
import type { ReactNode } from 'react'

export function AuthGuard({ children }: { children: ReactNode }) {
  const { isAuthenticated } = useAuth()
  if (!isAuthenticated) return <Navigate to="/login" replace />
  return <>{children}</>
}
