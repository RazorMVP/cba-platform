// web-react/src/app/features/system/TaxesPage.tsx
import { useState } from 'react'
import { PageHeader } from '@/shared/components/PageHeader'
import {
  useTaxComponents, useCreateTaxComponent, useUpdateTaxComponent,
  useTaxGroups, useCreateTaxGroup, useUpdateTaxGroup,
} from './api/useSystem'
import type {
  TaxComponent, CreateTaxComponentRequest,
  TaxGroup, TaxGroupMapping, CreateTaxGroupRequest,
} from './api/types'

type Tab = 'components' | 'groups'

// ── Tax Component modal ───────────────────────────────────────────────────────

function ComponentModal({
  initial,
  onClose,
}: {
  initial: TaxComponent | null
  onClose: () => void
}) {
  const create = useCreateTaxComponent()
  const update = useUpdateTaxComponent(initial?.id ?? '')

  const [name,       setName]       = useState(initial?.name ?? '')
  const [percentage, setPercentage] = useState(String(initial?.percentage ?? ''))
  const [startDate,  setStartDate]  = useState(initial?.startDate ?? '')

  async function save() {
    const body: CreateTaxComponentRequest = {
      name,
      percentage: Number(percentage),
      startDate,
    }
    if (initial) {
      await update.mutateAsync(body)
    } else {
      await create.mutateAsync(body)
    }
    onClose()
  }

  const isPending = create.isPending || update.isPending
  const valid = name && percentage && startDate

  return (
    <div
      style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', zIndex: 50, display: 'flex', alignItems: 'center', justifyContent: 'center' }}
      onClick={e => { if (e.target === e.currentTarget) onClose() }}
    >
      <div className="rounded-xl p-6 w-full max-w-md"
        style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)' }}>
        <h2 className="text-base font-semibold mb-4" style={{ color: 'var(--color-text)' }}>
          {initial ? 'Edit Tax Component' : 'New Tax Component'}
        </h2>

        <div className="space-y-3">
          <div>
            <label className="block text-xs mb-1" style={{ color: 'var(--color-muted)' }}>Name *</label>
            <input type="text" value={name} onChange={e => setName(e.target.value)}
              className="w-full px-3 py-2 rounded-lg text-sm outline-none"
              style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }} />
          </div>
          <div>
            <label className="block text-xs mb-1" style={{ color: 'var(--color-muted)' }}>Percentage (%) *</label>
            <input type="number" step="0.001" min="0" max="100" value={percentage}
              onChange={e => setPercentage(e.target.value)}
              className="w-full px-3 py-2 rounded-lg text-sm outline-none"
              style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }} />
          </div>
          <div>
            <label className="block text-xs mb-1" style={{ color: 'var(--color-muted)' }}>Start Date *</label>
            <input type="date" value={startDate} onChange={e => setStartDate(e.target.value)}
              className="w-full px-3 py-2 rounded-lg text-sm outline-none"
              style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }} />
          </div>
        </div>

        <div className="flex gap-2 justify-end mt-5">
          <button onClick={onClose} className="text-xs px-3 py-1.5 rounded-lg"
            style={{ color: 'var(--color-muted)', border: '1px solid var(--color-border)' }}>
            Cancel
          </button>
          <button onClick={save} disabled={isPending || !valid}
            className="text-xs px-3 py-1.5 rounded-lg text-white disabled:opacity-60"
            style={{ background: 'var(--color-primary)' }}>
            {isPending ? 'Saving…' : 'Save'}
          </button>
        </div>
      </div>
    </div>
  )
}

// ── Tax Group modal ───────────────────────────────────────────────────────────

const BLANK_MAPPING = (): TaxGroupMapping => ({
  taxComponentId: '',
  startDate: '',
})

function GroupModal({
  initial,
  components,
  onClose,
}: {
  initial: TaxGroup | null
  components: TaxComponent[]
  onClose: () => void
}) {
  const create = useCreateTaxGroup()
  const update = useUpdateTaxGroup(initial?.id ?? '')

  const [name, setName] = useState(initial?.name ?? '')
  const [mappings, setMappings] = useState<TaxGroupMapping[]>(
    initial?.taxGroupMappings?.length ? [...initial.taxGroupMappings] : [BLANK_MAPPING()]
  )

  function setMappingField<K extends keyof TaxGroupMapping>(
    idx: number, key: K, val: TaxGroupMapping[K]
  ) {
    setMappings(prev => prev.map((m, i) => i === idx ? { ...m, [key]: val } : m))
  }

  function addMapping() { setMappings(prev => [...prev, BLANK_MAPPING()]) }
  function removeMapping(idx: number) { setMappings(prev => prev.filter((_, i) => i !== idx)) }

  async function save() {
    const body: CreateTaxGroupRequest = {
      name,
      taxGroupMappings: mappings.filter(m => m.taxComponentId && m.startDate),
    }
    if (initial) {
      await update.mutateAsync(body)
    } else {
      await create.mutateAsync(body)
    }
    onClose()
  }

  const isPending = create.isPending || update.isPending

  return (
    <div
      style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', zIndex: 50, display: 'flex', alignItems: 'center', justifyContent: 'center' }}
      onClick={e => { if (e.target === e.currentTarget) onClose() }}
    >
      <div className="rounded-xl p-6 w-full max-w-lg overflow-y-auto"
        style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)', maxHeight: '90vh' }}>
        <h2 className="text-base font-semibold mb-4" style={{ color: 'var(--color-text)' }}>
          {initial ? 'Edit Tax Group' : 'New Tax Group'}
        </h2>

        <div className="mb-4">
          <label className="block text-xs mb-1" style={{ color: 'var(--color-muted)' }}>Group Name *</label>
          <input type="text" value={name} onChange={e => setName(e.target.value)}
            className="w-full px-3 py-2 rounded-lg text-sm outline-none"
            style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }} />
        </div>

        <div className="mb-4">
          <div className="flex items-center justify-between mb-2">
            <span className="text-xs font-semibold uppercase tracking-wider" style={{ color: 'var(--color-muted)' }}>
              Component Mappings
            </span>
            <button onClick={addMapping} className="text-xs px-2 py-1 rounded"
              style={{ color: 'var(--color-primary)', border: '1px solid var(--color-border)' }}>
              + Add
            </button>
          </div>

          <div className="space-y-2">
            {mappings.map((m, idx) => (
              <div key={idx} className="rounded-lg p-3 space-y-2"
                style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)' }}>
                <div className="flex gap-2 items-end">
                  <div className="flex-1">
                    <label className="block text-xs mb-1" style={{ color: 'var(--color-muted)' }}>Tax Component</label>
                    <select value={m.taxComponentId}
                      onChange={e => setMappingField(idx, 'taxComponentId', e.target.value)}
                      className="w-full px-2 py-1.5 rounded text-xs outline-none"
                      style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }}>
                      <option value="">Select component…</option>
                      {components.map(c => (
                        <option key={c.id} value={c.id}>{c.name} ({c.percentage}%)</option>
                      ))}
                    </select>
                  </div>
                  {mappings.length > 1 && (
                    <button onClick={() => removeMapping(idx)}
                      className="text-xs px-2 py-1.5 rounded"
                      style={{ color: 'var(--color-error)', border: '1px solid var(--color-border)' }}>
                      ×
                    </button>
                  )}
                </div>
                <div className="flex gap-2">
                  <div className="flex-1">
                    <label className="block text-xs mb-1" style={{ color: 'var(--color-muted)' }}>Start Date</label>
                    <input type="date" value={m.startDate}
                      onChange={e => setMappingField(idx, 'startDate', e.target.value)}
                      className="w-full px-2 py-1.5 rounded text-xs outline-none"
                      style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }} />
                  </div>
                  <div className="flex-1">
                    <label className="block text-xs mb-1" style={{ color: 'var(--color-muted)' }}>End Date (optional)</label>
                    <input type="date" value={m.endDate ?? ''}
                      onChange={e => setMappingField(idx, 'endDate', e.target.value || undefined)}
                      className="w-full px-2 py-1.5 rounded text-xs outline-none"
                      style={{ background: 'var(--bg-card)', border: '1px solid var(--color-border)', color: 'var(--color-text)' }} />
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>

        <div className="flex gap-2 justify-end">
          <button onClick={onClose} className="text-xs px-3 py-1.5 rounded-lg"
            style={{ color: 'var(--color-muted)', border: '1px solid var(--color-border)' }}>
            Cancel
          </button>
          <button onClick={save} disabled={isPending || !name}
            className="text-xs px-3 py-1.5 rounded-lg text-white disabled:opacity-60"
            style={{ background: 'var(--color-primary)' }}>
            {isPending ? 'Saving…' : 'Save'}
          </button>
        </div>
      </div>
    </div>
  )
}

// ── Main page ─────────────────────────────────────────────────────────────────

export default function TaxesPage() {
  const { data: componentsData, isLoading: loadingComponents } = useTaxComponents()
  const { data: groupsData,     isLoading: loadingGroups }     = useTaxGroups()

  const components: TaxComponent[] = (componentsData as { data?: TaxComponent[] } | undefined)?.data ?? []
  const groups:     TaxGroup[]     = (groupsData     as { data?: TaxGroup[]     } | undefined)?.data ?? []

  const [tab, setTab] = useState<Tab>('components')
  const [componentModal, setComponentModal] = useState<TaxComponent | null | 'new'>(null)
  const [groupModal,     setGroupModal]     = useState<TaxGroup     | null | 'new'>(null)

  const TABS: { key: Tab; label: string }[] = [
    { key: 'components', label: `Tax Components (${components.length})` },
    { key: 'groups',     label: `Tax Groups (${groups.length})` },
  ]

  if (loadingComponents || loadingGroups) return (
    <div className="p-8 text-sm" style={{ color: 'var(--color-muted)' }}>Loading…</div>
  )

  return (
    <div>
      <PageHeader
        title="Taxes"
        subtitle="Tax components and tax groups for fee and interest calculations"
        actions={
          tab === 'components'
            ? <button onClick={() => setComponentModal('new')}
                className="text-xs px-3 py-1.5 rounded-lg text-white"
                style={{ background: 'var(--color-primary)' }}>
                + New Component
              </button>
            : <button onClick={() => setGroupModal('new')}
                className="text-xs px-3 py-1.5 rounded-lg text-white"
                style={{ background: 'var(--color-primary)' }}>
                + New Group
              </button>
        }
      />

      {/* Tab bar */}
      <div className="flex gap-1 mb-5 p-1 rounded-xl w-fit"
        style={{ background: 'var(--bg-subtle)', border: '1px solid var(--color-border)' }}>
        {TABS.map(t => (
          <button key={t.key} onClick={() => setTab(t.key)}
            className="text-xs px-4 py-1.5 rounded-lg transition-colors"
            style={{
              background: tab === t.key ? 'var(--bg-card)' : 'transparent',
              color:      tab === t.key ? 'var(--color-text)' : 'var(--color-muted)',
              fontWeight: tab === t.key ? 600 : 400,
              border:     tab === t.key ? '1px solid var(--color-border)' : '1px solid transparent',
            }}>
            {t.label}
          </button>
        ))}
      </div>

      {/* Tax Components tab */}
      {tab === 'components' && (
        <div className="rounded-xl overflow-hidden" style={{ border: '1px solid var(--color-border)' }}>
          <table className="w-full text-sm border-collapse">
            <thead>
              <tr style={{ background: 'var(--bg-subtle)', borderBottom: '1px solid var(--color-border)' }}>
                {['Name', 'Percentage', 'Start Date', ''].map(h => (
                  <th key={h} className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider"
                    style={{ color: 'var(--color-muted)' }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {components.length === 0 && (
                <tr><td colSpan={4} className="px-4 py-8 text-center text-xs" style={{ color: 'var(--color-muted)' }}>
                  No tax components defined.
                </td></tr>
              )}
              {components.map(c => (
                <tr key={c.id} style={{ borderBottom: '1px solid var(--color-border)', background: 'var(--bg-card)' }}>
                  <td className="px-4 py-3 text-sm font-medium" style={{ color: 'var(--color-text)' }}>{c.name}</td>
                  <td className="px-4 py-3 text-xs tabular-nums" style={{ color: 'var(--color-text)' }}>{c.percentage}%</td>
                  <td className="px-4 py-3 text-xs" style={{ color: 'var(--color-muted)' }}>{c.startDate}</td>
                  <td className="px-4 py-3">
                    <button onClick={() => setComponentModal(c)} className="text-xs px-2 py-1 rounded"
                      style={{ color: 'var(--color-primary)', border: '1px solid var(--color-border)' }}>
                      Edit
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Tax Groups tab */}
      {tab === 'groups' && (
        <div className="rounded-xl overflow-hidden" style={{ border: '1px solid var(--color-border)' }}>
          {groups.length === 0 && (
            <div className="px-4 py-8 text-center text-xs" style={{ color: 'var(--color-muted)' }}>
              No tax groups defined.
            </div>
          )}
          {groups.map((group, idx) => (
            <div key={group.id} style={{ borderTop: idx > 0 ? '1px solid var(--color-border)' : undefined }}>
              {/* Group header */}
              <div className="flex items-center gap-3 px-4 py-3"
                style={{ background: 'var(--bg-card)' }}>
                <span className="text-sm font-medium flex-1" style={{ color: 'var(--color-text)' }}>{group.name}</span>
                <span className="text-xs" style={{ color: 'var(--color-muted)' }}>
                  {group.taxGroupMappings?.length ?? 0} component{group.taxGroupMappings?.length !== 1 ? 's' : ''}
                </span>
                <button onClick={() => setGroupModal(group)} className="text-xs px-2 py-1 rounded"
                  style={{ color: 'var(--color-primary)', border: '1px solid var(--color-border)' }}>
                  Edit
                </button>
              </div>
              {/* Mappings */}
              {group.taxGroupMappings?.map((m, i) => {
                const comp = components.find(c => c.id === m.taxComponentId)
                return (
                  <div key={i} className="flex items-center gap-4 px-6 py-2"
                    style={{ borderTop: '1px solid var(--color-border)', background: 'var(--bg-subtle)' }}>
                    <span className="text-xs flex-1" style={{ color: 'var(--color-text)' }}>
                      {m.taxComponentName ?? comp?.name ?? m.taxComponentId}
                    </span>
                    <span className="text-xs tabular-nums" style={{ color: 'var(--color-muted)' }}>
                      {m.startDate}{m.endDate ? ` → ${m.endDate}` : ''}
                    </span>
                  </div>
                )
              })}
            </div>
          ))}
        </div>
      )}

      {/* Modals */}
      {componentModal !== null && (
        <ComponentModal
          initial={componentModal === 'new' ? null : componentModal}
          onClose={() => setComponentModal(null)}
        />
      )}

      {groupModal !== null && (
        <GroupModal
          initial={groupModal === 'new' ? null : groupModal}
          components={components}
          onClose={() => setGroupModal(null)}
        />
      )}
    </div>
  )
}
