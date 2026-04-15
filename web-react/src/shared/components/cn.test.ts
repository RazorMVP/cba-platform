// web-react/src/shared/components/cn.test.ts
import { describe, it, expect } from 'vitest'
import { cn } from './cn'

describe('cn', () => {
  it('merges class names', () => {
    expect(cn('px-4', 'py-2')).toBe('px-4 py-2')
  })

  it('resolves Tailwind conflicts in favour of the last class', () => {
    expect(cn('px-2', 'px-4')).toBe('px-4')
  })

  it('handles conditional falsy values', () => {
    expect(cn('base', false && 'never', undefined, 'end')).toBe('base end')
  })
})
