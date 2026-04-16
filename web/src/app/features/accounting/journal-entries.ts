import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge';
import {
  AccountingService, JournalEntry, ManualJournalRequest, ManualJournalLine,
  GlAccount,
} from './accounting.service';

export interface JournalEntryGroup {
  transactionId: string;
  entryDate:     string;
  reference?:    string;
  comments?:     string;
  createdByType: string;
  reversed:      boolean;
  debits:        JournalEntry[];
  credits:       JournalEntry[];
  totalDebit:    number;
  totalCredit:   number;
}

interface JournalLine {
  glAccountId: string;
  amount:      number | null;
  comments:    string;
}

@Component({
  selector: 'app-journal-entries',
  standalone: true,
  imports: [CommonModule, FormsModule, StatusBadgeComponent],
  templateUrl: './journal-entries.html',
  styleUrl: './journal-entries.scss',
})
export class JournalEntriesComponent implements OnInit {
  private readonly svc = inject(AccountingService);

  groups:    JournalEntryGroup[] = [];
  loading    = true;
  error      = '';
  totalItems = 0;

  // Filter state
  dateFrom    = '';
  dateTo      = '';
  typeFilter  = '';   // '' | 'USER' | 'SYSTEM'
  glCodeQuery = '';

  // ── GL accounts (for modal selects) ───────────────────────────────────────
  glAccounts: GlAccount[] = [];
  get manualEligible(): GlAccount[] {
    return this.glAccounts.filter(a => a.manualEntriesAllowed && !a.disabled && a.usage === 'DETAIL');
  }

  // ── Reverse modal ──────────────────────────────────────────────────────────
  showReverseModal = false;
  reverseGroup:  JournalEntryGroup | null = null;
  reverseWorking = false;
  reverseError   = '';

  // ── Create manual entry modal ──────────────────────────────────────────────
  showCreateModal = false;
  createWorking   = false;
  createError     = '';

  entryDate   = '';
  entryRef    = '';
  entryNote   = '';
  debitLines:  JournalLine[] = [this.blankLine(), this.blankLine()];
  creditLines: JournalLine[] = [this.blankLine(), this.blankLine()];

  get debitTotal():  number { return this.sumLines(this.debitLines); }
  get creditTotal(): number { return this.sumLines(this.creditLines); }
  get isBalanced():  boolean {
    return this.debitTotal > 0 && this.debitTotal === this.creditTotal;
  }

  ngOnInit(): void {
    this.entryDate = new Date().toISOString().slice(0, 10);
    this.dateFrom  = new Date(Date.now() - 30 * 86400000).toISOString().slice(0, 10);
    this.dateTo    = new Date().toISOString().slice(0, 10);
    this.loadEntries();
    this.svc.listGlAccounts().subscribe({
      next: list => { this.glAccounts = list; },
    });
  }

  loadEntries(): void {
    this.loading = true;
    const params: Record<string, string> = {};
    if (this.dateFrom)   params['fromDate']     = this.dateFrom;
    if (this.dateTo)     params['toDate']       = this.dateTo;
    if (this.typeFilter) params['manualEntries'] = this.typeFilter === 'USER' ? 'true' : 'false';
    if (this.glCodeQuery) params['glAccountCode'] = this.glCodeQuery;

    this.svc.listJournalEntries(params).subscribe({
      next: page => {
        this.groups     = this.groupEntries(page.content ?? []);
        this.totalItems = page.totalElements ?? 0;
        this.loading    = false;
      },
      error: () => { this.error = 'Failed to load journal entries.'; this.loading = false; },
    });
  }

  private groupEntries(entries: JournalEntry[]): JournalEntryGroup[] {
    const map = new Map<string, JournalEntryGroup>();
    for (const e of entries) {
      if (!map.has(e.transactionId)) {
        map.set(e.transactionId, {
          transactionId: e.transactionId,
          entryDate:     e.entryDate,
          reference:     e.referenceNumber,
          comments:      e.comments,
          createdByType: e.createdByType,
          reversed:      e.reversed,
          debits:        [],
          credits:       [],
          totalDebit:    0,
          totalCredit:   0,
        });
      }
      const g = map.get(e.transactionId)!;
      if (e.type === 'DEBIT')  { g.debits.push(e);  g.totalDebit  += e.amount; }
      else                     { g.credits.push(e); g.totalCredit += e.amount; }
    }
    return Array.from(map.values()).sort(
      (a, b) => b.entryDate.localeCompare(a.entryDate));
  }

  // ── Reverse ────────────────────────────────────────────────────────────────

  openReverseModal(g: JournalEntryGroup): void {
    this.reverseGroup   = g;
    this.showReverseModal = true;
    this.reverseWorking = false;
    this.reverseError   = '';
  }

  submitReverse(): void {
    if (!this.reverseGroup) return;
    const anyEntry = this.reverseGroup.debits[0] ?? this.reverseGroup.credits[0];
    if (!anyEntry) return;
    this.reverseWorking = true;
    this.svc.reverseJournalEntry(anyEntry.id).subscribe({
      next: () => {
        this.showReverseModal = false;
        this.reverseWorking   = false;
        this.loadEntries();
      },
      error: () => { this.reverseError = 'Reversal failed.'; this.reverseWorking = false; },
    });
  }

  closeReverseModal(): void { if (!this.reverseWorking) this.showReverseModal = false; }

  // ── Create manual entry ────────────────────────────────────────────────────

  openCreateModal(): void {
    this.showCreateModal = true;
    this.createWorking   = false;
    this.createError     = '';
    this.entryRef        = '';
    this.entryNote       = '';
    this.entryDate       = new Date().toISOString().slice(0, 10);
    this.debitLines      = [this.blankLine(), this.blankLine()];
    this.creditLines     = [this.blankLine(), this.blankLine()];
  }

  addLine(side: 'debit' | 'credit'): void {
    if (side === 'debit') this.debitLines  = [...this.debitLines,  this.blankLine()];
    else                  this.creditLines = [...this.creditLines, this.blankLine()];
  }

  removeLine(side: 'debit' | 'credit', i: number): void {
    if (side === 'debit')  this.debitLines  = this.debitLines.filter((_,  idx) => idx !== i);
    else                   this.creditLines = this.creditLines.filter((_, idx) => idx !== i);
  }

  submitCreate(): void {
    if (!this.isBalanced || !this.entryDate) return;
    this.createWorking = true;
    const req: ManualJournalRequest = {
      transactionDate: this.entryDate,
      locale: 'en',
      dateFormat: 'yyyy-MM-dd',
      referenceNumber: this.entryRef  || undefined,
      comments:        this.entryNote || undefined,
      debits:  this.toLines(this.debitLines),
      credits: this.toLines(this.creditLines),
    };
    this.svc.createManualJournalEntry(req).subscribe({
      next: () => {
        this.showCreateModal = false;
        this.createWorking   = false;
        this.loadEntries();
      },
      error: () => { this.createError = 'Failed to post entry. Ensure accounts are valid and balanced.'; this.createWorking = false; },
    });
  }

  closeCreateModal(): void { if (!this.createWorking) this.showCreateModal = false; }

  // ── Helpers ────────────────────────────────────────────────────────────────

  createdByVariant(t: string): 'success' | 'neutral' {
    return t === 'USER' ? 'success' : 'neutral';
  }

  private blankLine(): JournalLine { return { glAccountId: '', amount: null, comments: '' }; }
  private sumLines(lines: JournalLine[]): number {
    return lines.reduce((s, l) => s + (l.amount ?? 0), 0);
  }
  private toLines(lines: JournalLine[]): ManualJournalLine[] {
    return lines
      .filter(l => l.glAccountId && (l.amount ?? 0) > 0)
      .map(l => ({ glAccountId: l.glAccountId, amount: l.amount!, comments: l.comments || undefined }));
  }
}
