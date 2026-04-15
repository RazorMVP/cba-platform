// web-react/src/app/features/placeholder/PlaceholderPage.tsx
import { useLocation } from 'react-router-dom'

export default function PlaceholderPage() {
  const { pathname } = useLocation()
  return (
    <div className="flex flex-col gap-3 p-8">
      <p className="font-display text-xl font-semibold" style={{ color: 'var(--color-text)' }}>
        Coming soon
      </p>
      <p className="text-sm" style={{ color: 'var(--color-muted)' }}>{pathname}</p>
    </div>
  )
}
