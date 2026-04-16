// web-react/src/app/features/system/CodesPage.tsx
import { useState } from 'react'
import { PageHeader } from '@/shared/components/PageHeader'
import {
  useCodes, useCodeValues, useCreateCode, useDeleteCode,
  useCreateCodeValue, useUpdateCodeValue, useDeleteCodeValue,
} from './api/useSystem'
import type { Code, CodeValue, CreateCodeValueRequest } from './api/types'

// ── Code values section — mounts lazily when accordion opens ──────────────────

function CodeValuesSection({ codeId, systemDefined }: { codeId: string; systemDefined: boolean }) {
  const { data, isLoading } = useCodeValues(codeId)
  const values: CodeValue[] = (data as { data?: CodeValue[] } | undefined)?.data ?? []

  const createVal = useCreateCodeValue(codeId)
  const [newName, setNewName] = useState('')
  const [newDesc, setNewDesc] = useState('')
  const [editingId, setEditingId] = useState<string | null>(null)
  const [editName, setEditName] = useState('')

  if (isLoading) return (
    <div className="px-4 py-3 text-xs" style={{ color: 'var(--color-muted)' }}>Loading values…</div>
  )

  return (
    <div className="border-t" style={{ borderColor: 'var(--color-border)' }}>
      {values.map(v => (
        <EditValueRow
          key={v.id}
          codeId={codeId}
          value={v}
          systemDefined={systemDefined}
          editing={editingId === v.id}
          editName={editingId === v.id ? editName : v.name}
          onStartEdit={() => { setEditingId(v.id); setEditName(v.name) }}
          onEditName={setEditName}
          onCancel={() => setEditingId(null)}
          onSaved={() => setEditingId(null)}
        />
      ))}

      {!systemDefined && (
        <div className="flex gap-2 px-4 py-3 items-center"
          style={{ borderTop: values.length ? '1px solid var(--color-border)' : undefined, background: 'var(--bg-subtle)' }}>
          <input
            type="text"
            placeholder="Value name…"
            value={newName}
            onChange={e => setNewName(e.target.value)}
            className="px-2 py-1 rounded text-xs outline-none"
            style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)', color: 'var(--color-text)', width: 160 }}
          />
          <input
            type="text"
            placeholder="Description (optional)"
            value={newDesc}
            onChange={e => setNewDesc(e.target.value)}
            className="px-2 py-1 rounded text-xs outline-none flex-1"
            style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }}
          />
          <button
            onClick={async () => {
              if (!newName) return
              const req: CreateCodeValueRequest = { name: newName, description: newDesc || undefined }
              await createVal.mutateAsync(req)
              setNewName('')
              setNewDesc('')
            }}
            disabled={createVal.isPending || !newName}
            className="text-xs px-2 py-1 rounded disabled:opacity-60 text-white"
            style={{ background: 'var(--color-primary)' }}
          >
            {createVal.isPending ? '…' : '+ Add'}
          </button>
        </div>
      )}
    </div>
  )
}

// ── Edit value row — extracts hooks that need valueId ─────────────────────────

function EditValueRow({ codeId, value, systemDefined, editing, editName, onStartEdit, onEditName, onCancel, onSaved }: {
  codeId: string
  value: CodeValue
  systemDefined: boolean
  editing: boolean
  editName: string
  onStartEdit: () => void
  onEditName: (v: string) => void
  onCancel: () => void
  onSaved: () => void
}) {
  const update = useUpdateCodeValue(codeId, value.id)
  const del    = useDeleteCodeValue(codeId, value.id)

  return (
    <div className="flex items-center gap-3 px-4 py-2.5"
      style={{ borderTop: '1px solid var(--color-border)', background: 'var(--bg-card)' }}>
      <span className="text-xs tabular-nums w-6" style={{ color: 'var(--color-muted)' }}>{value.position}</span>

      {editing ? (
        <>
          <input
            type="text"
            value={editName}
            onChange={e => onEditName(e.target.value)}
            autoFocus
            className="px-2 py-1 rounded text-xs outline-none flex-1"
            style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }}
          />
          <button
            onClick={async () => { await update.mutateAsync({ name: editName }); onSaved() }}
            disabled={update.isPending}
            className="text-xs px-2 py-1 rounded disabled:opacity-60 text-white"
            style={{ background: 'var(--color-primary)' }}
          >
            {update.isPending ? '…' : 'Save'}
          </button>
          <button onClick={onCancel} className="text-xs px-2 py-1 rounded"
            style={{ color: 'var(--color-muted)', border: '1px solid var(--color-border)' }}>
            Cancel
          </button>
        </>
      ) : (
        <>
          <span className="text-xs flex-1" style={{ color: 'var(--color-text)' }}>{value.name}</span>
          {value.description && (
            <span className="text-xs" style={{ color: 'var(--color-muted)' }}>{value.description}</span>
          )}
          {!systemDefined && (
            <>
              <button onClick={onStartEdit} className="text-xs px-2 py-1 rounded"
                style={{ color: 'var(--color-primary)', border: '1px solid var(--color-border)' }}>
                Edit
              </button>
              <button
                onClick={() => del.mutateAsync()}
                disabled={del.isPending}
                className="text-xs px-2 py-1 rounded disabled:opacity-60"
                style={{ color: 'var(--color-error)', border: '1px solid var(--color-border)' }}
              >
                {del.isPending ? '…' : 'Delete'}
              </button>
            </>
          )}
        </>
      )}
    </div>
  )
}

// ── Delete code row ───────────────────────────────────────────────────────────

function DeleteCodeButton({ code }: { code: Code }) {
  const del = useDeleteCode(code.id)
  if (code.systemDefined) return null
  return (
    <button
      onClick={e => { e.stopPropagation(); del.mutateAsync() }}
      disabled={del.isPending}
      className="text-xs px-2 py-1 rounded disabled:opacity-60"
      style={{ color: 'var(--color-error)', border: '1px solid var(--color-border)' }}
    >
      {del.isPending ? '…' : 'Delete'}
    </button>
  )
}

// ── Main page ─────────────────────────────────────────────────────────────────

export default function CodesPage() {
  const { data, isLoading } = useCodes()
  const codes: Code[] = (data as { data?: Code[] } | undefined)?.data ?? []

  const create = useCreateCode()
  const [expanded, setExpanded] = useState<Set<string>>(new Set())
  const [newCodeName, setNewCodeName] = useState('')
  const [search, setSearch] = useState('')

  function toggleExpand(id: string) {
    setExpanded(prev => {
      const next = new Set(prev)
      next.has(id) ? next.delete(id) : next.add(id)
      return next
    })
  }

  const filtered = codes.filter(c =>
    !search || c.name.toLowerCase().includes(search.toLowerCase())
  )

  if (isLoading) return <div className="p-8 text-sm" style={{ color: 'var(--color-muted)' }}>Loading…</div>

  return (
    <div>
      <PageHeader title="Codes & Values" subtitle="Extensible lookup tables" />

      {/* Create new code */}
      <div className="flex gap-2 mb-5">
        <input
          type="text"
          placeholder="Search codes…"
          value={search}
          onChange={e => setSearch(e.target.value)}
          className="px-3 py-2 rounded-lg text-sm outline-none"
          style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)', width: 220 }}
        />
        <div className="flex-1" />
        <input
          type="text"
          placeholder="New code name…"
          value={newCodeName}
          onChange={e => setNewCodeName(e.target.value)}
          className="px-3 py-2 rounded-lg text-sm outline-none"
          style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)', width: 200 }}
        />
        <button
          onClick={async () => {
            if (!newCodeName) return
            await create.mutateAsync({ name: newCodeName })
            setNewCodeName('')
          }}
          disabled={create.isPending || !newCodeName}
          className="text-xs px-3 py-1.5 rounded-lg text-white disabled:opacity-60"
          style={{ background: 'var(--color-primary)' }}
        >
          {create.isPending ? '…' : '+ New Code'}
        </button>
      </div>

      <div className="rounded-xl overflow-hidden" style={{ border: '1px solid var(--color-border)' }}>
        {filtered.length === 0 && (
          <div className="px-4 py-8 text-center text-xs" style={{ color: 'var(--color-muted)' }}>No codes found.</div>
        )}
        {filtered.map((code, idx) => (
          <div key={code.id} style={{ borderTop: idx > 0 ? '1px solid var(--color-border)' : undefined }}>
            {/* Accordion header */}
            <div
              className="flex items-center gap-3 px-4 py-3 cursor-pointer select-none"
              style={{ background: 'var(--bg-card)' }}
              onClick={() => toggleExpand(code.id)}
            >
              <span
                className="text-xs font-mono transition-transform"
                style={{ color: 'var(--color-muted)', display: 'inline-block', transform: expanded.has(code.id) ? 'rotate(90deg)' : 'none' }}
              >▶</span>
              <span className="text-sm font-medium flex-1" style={{ color: 'var(--color-text)' }}>{code.name}</span>
              {code.systemDefined && (
                <span className="text-xs px-2 py-0.5 rounded-full"
                  style={{ background: 'var(--bg-subtle)', color: 'var(--color-muted)', border: '1px solid var(--color-border)' }}>
                  System
                </span>
              )}
              <DeleteCodeButton code={code} />
            </div>

            {/* Accordion body — loads values lazily */}
            {expanded.has(code.id) && (
              <CodeValuesSection codeId={code.id} systemDefined={code.systemDefined} />
            )}
          </div>
        ))}
      </div>
    </div>
  )
}
