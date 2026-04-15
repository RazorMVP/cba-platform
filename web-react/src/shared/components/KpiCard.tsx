// web-react/src/shared/components/KpiCard.tsx
import { cn } from './cn'

interface KpiCardProps {
  label: string
  value: string | number
  delta?: string
  deltaPositive?: boolean
  className?: string
}

export function KpiCard({ label, value, delta, deltaPositive, className }: KpiCardProps) {
  return (
    <div
      className={cn('rounded-xl p-6', className)}
      style={{
        background: 'var(--bg-card)',
        border: '1px solid var(--color-border)',
      }}
    >
      <p className="text-xs font-semibold uppercase tracking-wider mb-2" style={{ color: 'var(--color-muted)' }}>
        {label}
      </p>
      <p className="font-display text-2xl font-bold tabular-nums" style={{ color: 'var(--color-text)' }}>
        {value}
      </p>
      {delta !== undefined ? (
        <p
          className="mt-1 text-xs font-medium tabular-nums"
          style={{ color: deltaPositive ? 'var(--color-success)' : 'var(--color-error)' }}
        >
          {delta}
        </p>
      ) : null}
    </div>
  )
}
