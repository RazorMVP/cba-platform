// web-react/src/shared/components/Modal.tsx
import { type ReactNode, useEffect, useRef } from 'react'
import { cn } from './cn'

interface ModalProps {
  open: boolean
  onClose: () => void
  title: string
  children: ReactNode
  footer?: ReactNode
  size?: 'sm' | 'md' | 'lg'
}

const sizeClasses = {
  sm: 'max-w-sm',
  md: 'max-w-md',
  lg: 'max-w-2xl',
}

export function Modal({ open, onClose, title, children, footer, size = 'md' }: ModalProps) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const onCloseRef = useRef(onClose)

  useEffect(() => {
    onCloseRef.current = onClose
  }, [onClose])

  useEffect(() => {
    const el = dialogRef.current
    if (!el) return
    if (open) {
      if (typeof el.showModal === 'function') el.showModal()
    } else if (el.open) {
      if (typeof el.close === 'function') el.close()
    }
  }, [open])

  useEffect(() => {
    const dialog = dialogRef.current
    if (!dialog) return
    const handleClose = () => onCloseRef.current()
    dialog.addEventListener('close', handleClose)
    return () => dialog.removeEventListener('close', handleClose)
  }, []) // empty — registers once; always calls current onClose via ref

  return (
    <dialog
      ref={dialogRef}
      className={cn('rounded-xl w-full p-0 backdrop:bg-black/50', sizeClasses[size])}
      style={{
        background: 'var(--bg-card)',
        border: '1px solid var(--color-border)',
        boxShadow: '0 8px 32px rgba(0,0,0,0.16)',
      }}
      onClick={e => {
        if (e.target === dialogRef.current) { onClose() }
      }}
    >
      <div className="flex items-center justify-between px-6 py-4" style={{ borderBottom: '1px solid var(--color-border)' }}>
        <h3 className="font-display font-semibold text-lg" style={{ color: 'var(--color-text)' }}>
          {title}
        </h3>
        <button
          onClick={onClose}
          className="flex items-center justify-center rounded-md w-8 h-8 text-lg transition-colors hover:bg-[var(--bg-subtle)]"
          style={{ color: 'var(--color-muted)' }}
          aria-label="Close"
        >
          ×
        </button>
      </div>
      <div className="px-6 py-5">{children}</div>
      {footer ? (
        <div className="px-6 py-4 flex items-center justify-end gap-3" style={{ borderTop: '1px solid var(--color-border)' }}>
          {footer}
        </div>
      ) : null}
    </dialog>
  )
}
