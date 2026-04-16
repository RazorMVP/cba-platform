// web-react/src/shared/components/PageHeader.tsx
import { type ReactNode } from 'react'
import { cn } from './cn'

interface PageHeaderProps {
  title: string
  subtitle?: string
  actions?: ReactNode
  className?: string
}

export function PageHeader({ title, subtitle, actions, className }: PageHeaderProps) {
  return (
    <div className={cn('flex items-start justify-between gap-4 mb-8', className)}>
      <div>
        <h1 className="font-display text-xl font-semibold" style={{ color: 'var(--color-text)' }}>
          {title}
        </h1>
        {subtitle ? (
          <p className="mt-1 text-sm" style={{ color: 'var(--color-muted)' }}>
            {subtitle}
          </p>
        ) : null}
      </div>
      {actions ? <div className="flex items-center gap-3 shrink-0">{actions}</div> : null}
    </div>
  )
}
