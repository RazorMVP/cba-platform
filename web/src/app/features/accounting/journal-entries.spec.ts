import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { JournalEntriesComponent } from './journal-entries';
import { AccountingService, JournalEntry, GlAccount } from './accounting.service';

type Svc = Record<
  'listJournalEntries' | 'listGlAccounts' | 'createManualJournalEntry' | 'reverseJournalEntry',
  ReturnType<typeof vi.fn>
>;

function entry(over: Partial<JournalEntry> = {}): JournalEntry {
  return {
    id: 'je1', transactionId: 'tx1', transactionDate: '2026-06-01', postedAt: '2026-06-01T10:00:00Z',
    glAccountId: 'gl1', glAccountCode: '1001', glAccountName: 'Cash', entryType: 'DEBIT', amount: 100,
    currencyCode: 'USD', entityType: 'MANUAL', manual: true, reversed: false, ...over,
  };
}

function acc(over: Partial<GlAccount> = {}): GlAccount {
  return {
    id: 'gl1', glCode: '1001', name: 'Cash', accountType: 'ASSET', usage: 'DETAIL',
    manualEntriesAllowed: true, disabled: false, ...over,
  };
}

function page(content: JournalEntry[]) {
  return of(content);
}

describe('JournalEntriesComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = {
      listJournalEntries: vi.fn().mockReturnValue(
        page([entry({ id: 'd', entryType: 'DEBIT', amount: 100 }), entry({ id: 'c', entryType: 'CREDIT', amount: 100 })]),
      ),
      listGlAccounts: vi.fn().mockReturnValue(of([acc(), acc({ id: 'gl2', glCode: '4001', name: 'Fees' })])),
      createManualJournalEntry: vi.fn().mockReturnValue(of([])),
      reverseJournalEntry: vi.fn().mockReturnValue(of({ reversedTransactionId: 'tx1', reversalTransactionId: 'REV-1', lines: [] })),
    };
    TestBed.configureTestingModule({
      imports: [JournalEntriesComponent],
      providers: [provideRouter([]), { provide: AccountingService, useValue: svc }],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(JournalEntriesComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads journal entries and GL accounts on init', () => {
    const c = make();
    expect(svc.listJournalEntries).toHaveBeenCalledWith(c.dateFrom, c.dateTo);
    expect(svc.listGlAccounts).toHaveBeenCalled();
    expect(c.glAccounts).toHaveLength(2);
    expect(c.totalItems).toBe(1); // one journal (two lines)
    expect(c.loading).toBe(false);
  });

  it('sets an error when loading fails', () => {
    svc.listJournalEntries.mockReturnValue(throwError(() => new Error('x')));
    const c = make();
    expect(c.error).toBe('Failed to load journal entries.');
    expect(c.loading).toBe(false);
  });

  describe('grouping by transaction', () => {
    it('splits entries of one transaction into debit/credit sides with totals', () => {
      const c = make();
      expect(c.groups).toHaveLength(1);
      const g = c.groups[0];
      expect(g.transactionId).toBe('tx1');
      expect(g.debits).toHaveLength(1);
      expect(g.credits).toHaveLength(1);
      expect(g.totalDebit).toBe(100);
      expect(g.totalCredit).toBe(100);
    });

    it('keeps separate transactions in separate groups, newest date first', () => {
      svc.listJournalEntries.mockReturnValue(page([
        entry({ id: 'a', transactionId: 'old', transactionDate: '2026-01-01' }),
        entry({ id: 'b', transactionId: 'new', transactionDate: '2026-06-01' }),
      ]));
      const c = make();
      expect(c.groups.map(g => g.transactionId)).toEqual(['new', 'old']);
    });

    it('maps API fields: manual → USER, sub-ledger → SYSTEM, reversal lines flag the group', () => {
      svc.listJournalEntries.mockReturnValue(page([
        entry({ id: 'm', transactionId: 'MJ-1' }),
        entry({ id: 's', transactionId: 'GL-1', manual: false, entityType: 'ACCOUNT', currencyCode: 'KES' }),
        entry({ id: 'r', transactionId: 'REV-1', reversalOfId: 'm' }),
      ]));
      const c = make();
      const byId = new Map(c.groups.map(g => [g.transactionId, g]));
      expect(byId.get('MJ-1')!.createdByType).toBe('USER');
      expect(byId.get('GL-1')!.createdByType).toBe('SYSTEM');
      expect(byId.get('GL-1')!.currencyCode).toBe('KES');
      expect(byId.get('REV-1')!.isReversal).toBe(true);
      expect(byId.get('MJ-1')!.isReversal).toBe(false);
    });
  });

  describe('filters (applied client-side; the API filters by date only)', () => {
    beforeEach(() => {
      svc.listJournalEntries.mockReturnValue(page([
        entry({ id: 'm', transactionId: 'MJ-1', glAccountCode: '5003' }),
        entry({ id: 's', transactionId: 'GL-1', manual: false, entityType: 'ACCOUNT', glAccountCode: '2001' }),
      ]));
    });

    it('type filter keeps only manual or only automated journals', () => {
      const c = make();
      c.typeFilter = 'SYSTEM';
      c.loadEntries();
      expect(c.groups.map(g => g.transactionId)).toEqual(['GL-1']);
    });

    it('GL code filter matches any line of the journal', () => {
      const c = make();
      c.glCodeQuery = '500';
      c.loadEntries();
      expect(c.groups.map(g => g.transactionId)).toEqual(['MJ-1']);
    });
  });

  describe('manual entry balance validation', () => {
    it('isBalanced is false when debits do not equal credits', () => {
      const c = make();
      c.openCreateModal();
      c.debitLines = [{ glAccountId: 'gl1', amount: 100, comments: '' }];
      c.creditLines = [{ glAccountId: 'gl2', amount: 60, comments: '' }];
      expect(c.debitTotal).toBe(100);
      expect(c.creditTotal).toBe(60);
      expect(c.isBalanced).toBe(false);
    });

    it('isBalanced is true when both sides match and are positive', () => {
      const c = make();
      c.openCreateModal();
      c.debitLines = [{ glAccountId: 'gl1', amount: 80, comments: '' }];
      c.creditLines = [{ glAccountId: 'gl2', amount: 80, comments: '' }];
      expect(c.isBalanced).toBe(true);
    });

    it('isBalanced is false when both sides are zero', () => {
      const c = make();
      c.openCreateModal();
      expect(c.debitTotal).toBe(0);
      expect(c.isBalanced).toBe(false);
    });
  });

  it('manualEligible returns only manual-entry, enabled, DETAIL accounts', () => {
    const c = make();
    c.glAccounts = [
      acc({ id: 'ok', manualEntriesAllowed: true, usage: 'DETAIL', disabled: false }),
      acc({ id: 'noManual', manualEntriesAllowed: false }),
      acc({ id: 'disabled', disabled: true }),
      acc({ id: 'header', usage: 'HEADER' }),
    ];
    expect(c.manualEligible.map(a => a.id)).toEqual(['ok']);
  });

  it('addLine and removeLine mutate the chosen side', () => {
    const c = make();
    c.openCreateModal();
    expect(c.debitLines).toHaveLength(2);
    c.addLine('debit');
    expect(c.debitLines).toHaveLength(3);
    c.removeLine('debit', 0);
    expect(c.debitLines).toHaveLength(2);
  });

  describe('submitCreate', () => {
    it('does nothing when the entry is unbalanced', () => {
      const c = make();
      c.openCreateModal();
      c.debitLines = [{ glAccountId: 'gl1', amount: 100, comments: '' }];
      c.creditLines = [{ glAccountId: 'gl2', amount: 50, comments: '' }];
      c.submitCreate();
      expect(svc.createManualJournalEntry).not.toHaveBeenCalled();
    });

    it('posts a balanced entry, dropping empty lines, and reloads', () => {
      const c = make();
      c.openCreateModal();
      c.entryDate = '2026-06-10';
      c.entryCurrency = 'kes';
      c.entryRef = 'R1';
      c.debitLines = [{ glAccountId: 'gl1', amount: 100, comments: 'd' }, { glAccountId: '', amount: null, comments: '' }];
      c.creditLines = [{ glAccountId: 'gl2', amount: 100, comments: '' }];
      c.submitCreate();
      expect(svc.createManualJournalEntry).toHaveBeenCalledWith(
        expect.objectContaining({
          transactionDate: '2026-06-10',
          currencyCode: 'KES',
          referenceNumber: 'R1',
          debits: [{ glCode: '1001', amount: 100, description: 'd' }],
          credits: [{ glCode: '4001', amount: 100, description: undefined }],
        }),
      );
      expect(c.showCreateModal).toBe(false);
      expect(svc.listJournalEntries).toHaveBeenCalledTimes(2);
    });

    it('does nothing without a 3-letter currency', () => {
      const c = make();
      c.openCreateModal();
      c.entryCurrency = 'US';
      c.debitLines = [{ glAccountId: 'gl1', amount: 100, comments: '' }];
      c.creditLines = [{ glAccountId: 'gl2', amount: 100, comments: '' }];
      c.submitCreate();
      expect(svc.createManualJournalEntry).not.toHaveBeenCalled();
    });

    it('surfaces an error on failure', () => {
      svc.createManualJournalEntry.mockReturnValue(throwError(() => new Error('x')));
      const c = make();
      c.openCreateModal();
      c.debitLines = [{ glAccountId: 'gl1', amount: 100, comments: '' }];
      c.creditLines = [{ glAccountId: 'gl2', amount: 100, comments: '' }];
      c.submitCreate();
      expect(c.createError).toContain('Failed to post entry');
      expect(c.createWorking).toBe(false);
    });
  });

  describe('submitReverse', () => {
    it('reverses using an entry id from the group and reloads', () => {
      const c = make();
      const g = c.groups[0];
      c.openReverseModal(g);
      c.submitReverse();
      expect(svc.reverseJournalEntry).toHaveBeenCalledWith(g.debits[0].id);
      expect(c.showReverseModal).toBe(false);
      expect(svc.listJournalEntries).toHaveBeenCalledTimes(2);
    });

    it('surfaces an error on failure', () => {
      svc.reverseJournalEntry.mockReturnValue(throwError(() => new Error('x')));
      const c = make();
      c.openReverseModal(c.groups[0]);
      c.submitReverse();
      expect(c.reverseError).toBe('Reversal failed.');
      expect(c.reverseWorking).toBe(false);
    });

    it("shows the API's own message (e.g. a sub-ledger journal can't be reversed here)", () => {
      svc.reverseJournalEntry.mockReturnValue(throwError(() => ({
        error: { errors: [{ code: 'SUBLEDGER_JOURNAL_NOT_REVERSIBLE', message: 'reverse the source transaction instead' }] },
      })));
      const c = make();
      c.openReverseModal(c.groups[0]);
      c.submitReverse();
      expect(c.reverseError).toBe('reverse the source transaction instead');
    });
  });

  it('createdByVariant distinguishes USER from SYSTEM', () => {
    const c = make();
    expect(c.createdByVariant('USER')).toBe('success');
    expect(c.createdByVariant('SYSTEM')).toBe('neutral');
  });
});
