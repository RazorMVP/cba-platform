// web-react/src/shared/components/DataTable.tsx
import { type ReactNode } from 'react'
import { cn } from './cn'

export interface ColumnDef<T> {
  key: string
  header: string
  cell: (row: T) => ReactNode
  className?: string
}

interface DataTableProps<T> {
  columns: ColumnDef<T>[]
  data: T[]
  emptyMessage?: string
  loading?: boolean
  className?: string
  getRowKey?: (row: T, index: number) => string
}

export function DataTable<T>({
  columns,
  data,
  emptyMessage = 'No data',
  loading = false,
  className,
  getRowKey = (_row, i) => String(i),
}: DataTableProps<T>) {
  return (
    <div className={cn('w-full overflow-x-auto', className)}>
      <table
        className="w-full text-sm"
        style={{ borderCollapse: 'collapse' }}
      >
        <thead>
          <tr style={{ borderBottom: '1px solid var(--color-border)' }}>
            {columns.map(col => (
              <th
                key={col.key}
                scope="col"
                className={cn(
                  'px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider',
                  col.className,
                )}
                style={{ color: 'var(--color-muted)' }}
              >
                {col.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {loading ? (
            <tr>
              <td colSpan={columns.length} className="px-4 py-8 text-center text-sm" style={{ color: 'var(--color-muted)' }}>
                Loading…
              </td>
            </tr>
          ) : data.length === 0 ? (
            <tr>
              <td colSpan={columns.length} className="px-4 py-8 text-center text-sm" style={{ color: 'var(--color-muted)' }}>
                {emptyMessage}
              </td>
            </tr>
          ) : (
            data.map((row, i) => (
              <tr
                key={getRowKey(row, i)}
                className="transition-colors hover:bg-[var(--bg-subtle)]"
                style={{ borderBottom: '1px solid var(--color-border)', height: 44 }}
              >
                {columns.map(col => (
                  <td
                    key={col.key}
                    className={cn('px-4 py-2 tabular-nums', col.className)}
                    style={{ color: 'var(--color-text)' }}
                  >
                    {col.cell(row)}
                  </td>
                ))}
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  )
}
