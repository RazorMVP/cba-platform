import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { TrialBalanceComponent } from './trial-balance';
import { AccountingService, TrialBalanceResponse, TrialBalanceRow } from './accounting.service';

type Svc = Record<'getTrialBalance', ReturnType<typeof vi.fn>>;

function row(over: Partial<TrialBalanceRow> = {}): TrialBalanceRow {
  return {
    glCode: '1001', accountName: 'Cash', accountType: 'ASSET',
    openingBalance: 100, debitMovement: 50, creditMovement: 20, closingBalance: 130, ...over,
  };
}

function report(over: Partial<TrialBalanceResponse> = {}): TrialBalanceResponse {
  return {
    fromDate: '2026-06-01', toDate: '2026-06-30',
    rows: [row()],
    totalDebitMovement: 50, totalCreditMovement: 20,
    totalClosingDebit: 130, totalClosingCredit: 0, balanced: true, ...over,
  };
}

describe('TrialBalanceComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = { getTrialBalance: vi.fn().mockReturnValue(of(report())) };
    TestBed.configureTestingModule({
      imports: [TrialBalanceComponent],
      providers: [provideRouter([]), { provide: AccountingService, useValue: svc }],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(TrialBalanceComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads the trial balance on init with default date range', () => {
    const c = make();
    expect(svc.getTrialBalance).toHaveBeenCalledWith(c.fromDate, c.toDate);
    expect(c.report).not.toBeNull();
    expect(c.loading).toBe(false);
  });

  it('does not load when a date is missing', () => {
    const c = make();
    svc.getTrialBalance.mockClear();
    c.fromDate = '';
    c.load();
    expect(svc.getTrialBalance).not.toHaveBeenCalled();
  });

  it('sets an error when loading fails', () => {
    svc.getTrialBalance.mockReturnValue(throwError(() => new Error('x')));
    const c = make();
    expect(c.error).toBe('Failed to load trial balance.');
    expect(c.loading).toBe(false);
  });

  describe('groupedRows', () => {
    it('returns empty when there is no report', () => {
      const c = make();
      c.report = null;
      expect(c.groupedRows()).toEqual([]);
    });

    it('groups rows by account type in canonical order', () => {
      const c = make();
      c.report = report({
        rows: [
          row({ glCode: '6001', accountType: 'EXPENSE' }),
          row({ glCode: '1001', accountType: 'ASSET' }),
          row({ glCode: '2001', accountType: 'LIABILITY' }),
          row({ glCode: '1002', accountType: 'ASSET' }),
        ],
      });
      const groups = c.groupedRows();
      expect(groups.map(g => g.type)).toEqual(['ASSET', 'LIABILITY', 'EXPENSE']);
      expect(groups[0].rows).toHaveLength(2);
    });
  });

  describe('subtotal', () => {
    it('sums a numeric field across rows', () => {
      const c = make();
      const rows = [
        row({ debitMovement: 50, closingBalance: 130 }),
        row({ debitMovement: 25, closingBalance: 70 }),
      ];
      expect(c.subtotal(rows, 'debitMovement')).toBe(75);
      expect(c.subtotal(rows, 'closingBalance')).toBe(200);
    });

    it('coerces string-typed numbers before summing', () => {
      const c = make();
      const rows = [
        row({ creditMovement: '10' as unknown as number }),
        row({ creditMovement: '5' as unknown as number }),
      ];
      expect(c.subtotal(rows, 'creditMovement')).toBe(15);
    });
  });

  describe('exportCsv', () => {
    it('does nothing without a report', () => {
      const c = make();
      c.report = null;
      const anchorSpy = vi.spyOn(document, 'createElement');
      c.exportCsv();
      expect(anchorSpy).not.toHaveBeenCalled();
      anchorSpy.mockRestore();
    });

    it('builds a blob with header, rows and totals and triggers a download', () => {
      const c = make();
      c.report = report({
        rows: [row({ glCode: '1001', accountName: 'Cash', accountType: 'ASSET', openingBalance: 100, debitMovement: 50, creditMovement: 20, closingBalance: 130 })],
        totalDebitMovement: 50, totalCreditMovement: 20,
      });

      // URL.createObjectURL receives the constructed Blob — read its content back.
      let csv = '';
      const click = vi.fn();
      vi.spyOn(document, 'createElement').mockReturnValue({ href: '', download: '', click } as unknown as HTMLAnchorElement);
      const urlSpy = vi.spyOn(URL, 'createObjectURL').mockImplementation((blob: Blob | MediaSource) => {
        csv = (blob as unknown as { __parts?: string[] }).__parts?.[0] ?? '';
        return 'blob:fake';
      });

      // Stash the Blob constructor parts on the instance for inspection (class-based mock).
      const RealBlob = globalThis.Blob;
      class TestBlob extends RealBlob {
        __parts: BlobPart[];
        constructor(parts: BlobPart[], opts?: BlobPropertyBag) {
          super(parts, opts);
          this.__parts = parts;
        }
      }
      vi.stubGlobal('Blob', TestBlob);

      c.exportCsv();

      expect(csv).toContain('GL Code,Account Name,Account Type');
      expect(csv).toContain('"1001","Cash","ASSET",100,50,20,130');
      expect(csv).toContain(',,TOTALS,,50,20,');
      expect(click).toHaveBeenCalled();

      vi.unstubAllGlobals();
      urlSpy.mockRestore();
      vi.restoreAllMocks();
    });
  });
});
