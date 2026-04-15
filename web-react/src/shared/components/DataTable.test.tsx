// web-react/src/shared/components/DataTable.test.tsx
import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { DataTable, type ColumnDef } from './DataTable'

interface User { id: string; name: string }

const columns: ColumnDef<User>[] = [
  { key: 'name', header: 'Name', cell: row => row.name },
]

describe('DataTable', () => {
  it('renders column headers', () => {
    render(<DataTable columns={columns} data={[]} />)
    expect(screen.getByText('Name')).toBeInTheDocument()
  })

  it('renders row data', () => {
    const data: User[] = [{ id: '1', name: 'Alice' }]
    render(<DataTable columns={columns} data={data} />)
    expect(screen.getByText('Alice')).toBeInTheDocument()
  })

  it('shows empty state when data is empty', () => {
    render(<DataTable columns={columns} data={[]} emptyMessage="No users found" />)
    expect(screen.getByText('No users found')).toBeInTheDocument()
  })
})
