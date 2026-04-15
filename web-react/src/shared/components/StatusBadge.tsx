// web-react/src/shared/components/StatusBadge.tsx
import { cn } from './cn'

export type BadgeVariant = 'success' | 'warning' | 'error' | 'info' | 'neutral' | 'primary'

interface StatusBadgeProps {
  label: string
  variant: BadgeVariant
  className?: string
}

const variantStyles: Record<BadgeVariant, string> = {
  success: 'badge-success bg-[var(--color-success-bg)] text-[var(--color-success)]',
  warning: 'badge-warning bg-[var(--color-warning-bg)] text-[var(--color-warning)]',
  error:   'badge-error bg-[var(--color-error-bg)] text-[var(--color-error)]',
  info:    'badge-info bg-[var(--color-info-bg)] text-[var(--color-info)]',
  neutral: 'badge-neutral bg-[var(--bg-subtle)] text-[var(--color-muted)]',
  primary: 'badge-primary bg-[var(--color-primary)] text-white',
}

export function StatusBadge({ label, variant, className }: StatusBadgeProps) {
  return (
    <span
      className={cn(
        'inline-flex items-center px-2 py-0.5 rounded text-xs font-medium',
        variantStyles[variant],
        className,
      )}
    >
      {label}
    </span>
  )
}
