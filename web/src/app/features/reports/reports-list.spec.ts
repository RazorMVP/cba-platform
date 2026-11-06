import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { ReportsListComponent } from './reports-list';
import { ReportService, Report, ReportParameter } from './report.service';

type Svc = Record<
  'listReports' | 'runReport' | 'exportReport' | 'deleteReport' | 'createReport' | 'enableReport',
  ReturnType<typeof vi.fn>
>;

function param(over: Partial<ReportParameter> = {}): ReportParameter {
  return { id: 'p1', parameterName: 'branch', parameterType: 'STRING', optional: false, ...over };
}
function report(over: Partial<Report> = {}): Report {
  return {
    id: 'r1', reportName: 'Active Loans', reportType: 'TABLE', reportCategory: 'LOAN',
    description: 'all active loans', coreReport: false, useReport: true,
    reportParameters: [param()], ...over,
  };
}

describe('ReportsListComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = {
      listReports: vi.fn().mockReturnValue(of([report()])),
      runReport: vi.fn().mockReturnValue(of({ rows: [{ name: 'Loan A', balance: 100 }], truncated: false })),
      exportReport: vi.fn().mockReturnValue(of(new Blob(['a,b']))),
      createReport: vi.fn().mockReturnValue(of({ id: 'new1', reportName: 'Mine', reportType: 'TABLE', coreReport: false, useReport: false, reportParameters: [] })),
      enableReport: vi.fn().mockReturnValue(of({ id: 'new1', reportName: 'Mine', reportType: 'TABLE', coreReport: false, useReport: true, reportParameters: [] })),
      deleteReport: vi.fn().mockReturnValue(of(void 0)),
    };
    TestBed.configureTestingModule({
      imports: [ReportsListComponent],
      providers: [{ provide: ReportService, useValue: svc }],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(ReportsListComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads + filters reports on init', () => {
    const c = make();
    expect(svc.listReports).toHaveBeenCalled();
    expect(c.reports).toHaveLength(1);
    expect(c.filtered).toHaveLength(1);
    expect(c.loading).toBe(false);
  });

  it('sets an error when loading fails', () => {
    svc.listReports.mockReturnValue(throwError(() => new Error('x')));
    const c = make();
    expect(c.error).toBe('Failed to load reports.');
    expect(c.loading).toBe(false);
  });

  it('categories derive distinct API categories, ordered by label', () => {
    const c = make();
    c.reports = [
      report({ id: 'a', reportCategory: 'SAVINGS' }),
      report({ id: 'b', reportCategory: 'LOAN' }),
      report({ id: 'd', reportCategory: 'LOAN' }),
      report({ id: 'e', reportCategory: 'MIXED' }),
      report({ id: 'f', reportCategory: undefined }),
    ];
    expect(c.categories).toEqual(['MIXED', 'LOAN', 'SAVINGS']); // General, Loans, Savings
  });

  it('categoryLabel shows readable names for the API values', () => {
    const c = make();
    expect(c.categoryLabel('LOAN')).toBe('Loans');
    expect(c.categoryLabel('CUSTOMER')).toBe('Customers');
    expect(c.categoryLabel('MIXED')).toBe('General');
    expect(c.categoryLabel(undefined)).toBe('');
  });

  it('renders the category chip label (the API sends reportCategory)', () => {
    svc.listReports.mockReturnValue(of([report({ reportCategory: 'ACCOUNTING' })]));
    const fixture = TestBed.createComponent(ReportsListComponent);
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('.category-chip')?.textContent?.trim()).toBe('Accounting');
  });

  it('applyFilter narrows by category and search', () => {
    const c = make();
    c.reports = [
      report({ id: 'a', reportName: 'Loan Book', reportCategory: 'LOAN', description: '' }),
      report({ id: 'b', reportName: 'Deposit Sum', reportCategory: 'SAVINGS', description: '' }),
    ];
    c.categoryFilter = 'LOAN';
    c.applyFilter();
    expect(c.filtered.map(r => r.id)).toEqual(['a']);
    c.categoryFilter = '';
    c.searchQuery = 'deposit';
    c.applyFilter();
    expect(c.filtered.map(r => r.id)).toEqual(['b']);
  });

  describe('run modal', () => {
    it('openRunModal seeds param entries from defaults', () => {
      const c = make();
      c.openRunModal(report({ reportParameters: [param({ defaultValue: '001' })] }));
      expect(c.activeModal).toBe('run');
      expect(c.paramEntries).toHaveLength(1);
      expect(c.paramEntries[0].value).toBe('001');
    });

    it('runReport blocks on missing required params', () => {
      const c = make();
      c.openRunModal(report({ reportParameters: [param({ parameterName: 'branch', optional: false })] }));
      c.paramEntries[0].value = '';
      c.runReport();
      expect(c.runError).toContain('Required');
      expect(svc.runReport).not.toHaveBeenCalled();
    });

    it('runReport executes and derives result columns (schema-on-read)', () => {
      const c = make();
      c.openRunModal(report());
      c.paramEntries[0].value = '001';
      c.runReport();
      expect(svc.runReport).toHaveBeenCalledWith('Active Loans', { branch: '001' });
      expect(c.results).toHaveLength(1);
      expect(c.resultCols).toEqual(['name', 'balance']);
      expect(c.hasRun).toBe(true);
    });

    it('runReport surfaces an execution error', () => {
      svc.runReport.mockReturnValue(throwError(() => new Error('x')));
      const c = make();
      c.openRunModal(report());
      c.paramEntries[0].value = '001';
      c.runReport();
      expect(c.runError).toContain('Report execution failed');
      expect(c.running).toBe(false);
    });

    it('exportReport downloads through the service (token-carrying HttpClient), not window.open', () => {
      const openSpy = vi.spyOn(window, 'open');
      const createUrl = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:x');
      const revokeUrl = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
      const c = make();
      c.openRunModal(report());
      c.paramEntries[0].value = '001';
      c.exportFormat = 'xlsx';
      c.exportReport();
      expect(svc.exportReport).toHaveBeenCalledWith('Active Loans', 'xlsx', { branch: '001' });
      expect(openSpy).not.toHaveBeenCalled();
      expect(createUrl).toHaveBeenCalled();
      openSpy.mockRestore(); createUrl.mockRestore(); revokeUrl.mockRestore();
    });

    it('shows that the result was truncated at the row limit', () => {
      svc.runReport.mockReturnValue(of({ rows: [{ n: 1 }], truncated: true }));
      const c = make();
      c.openRunModal(report());
      c.paramEntries[0].value = '001';
      c.runReport();
      expect(c.truncated).toBe(true);
    });
  });

  describe('create and enable (custom reports)', () => {
    it('New Report creates a disabled report and lists it', () => {
      const fixture = TestBed.createComponent(ReportsListComponent);
      fixture.detectChanges();
      expect((fixture.nativeElement as HTMLElement).textContent).toContain('New Report');
      const c = fixture.componentInstance;
      c.openCreateModal();
      c.submitCreate();
      expect(c.createError).toContain('required');
      c.createForm.reportName = 'Mine';
      c.createForm.reportSql = 'SELECT status FROM loans WHERE status = :s';
      c.addParam();
      c.createForm.reportParameters[0].parameterName = 's';
      c.submitCreate();
      expect(svc.createReport).toHaveBeenCalledWith(expect.objectContaining({ reportName: 'Mine' }));
      expect(c.reports.some(r => r.id === 'new1' && r.useReport === false)).toBe(true);
      expect(c.activeModal).toBeNull();
    });

    it('enable replaces the report; a refusal (same admin) is shown', () => {
      const c = make();
      c.enable({ id: 'new1', reportName: 'Mine', reportType: 'TABLE', coreReport: false, useReport: false, reportParameters: [] });
      expect(svc.enableReport).toHaveBeenCalledWith('new1');
      svc.enableReport.mockReturnValue(throwError(() => ({ error: { errors: [{ message: 'must be enabled by a different administrator' }] } })));
      c.enable({ id: 'new1', reportName: 'Mine', reportType: 'TABLE', coreReport: false, useReport: false, reportParameters: [] });
      expect(c.enableError).toContain('different administrator');
    });
  });

  describe('delete modal', () => {
    it('submitDelete removes the report', () => {
      const c = make();
      c.reports = [report({ id: 'r1' })];
      c.applyFilter();
      c.openDeleteModal(report({ id: 'r1' }));
      c.submitDelete();
      expect(svc.deleteReport).toHaveBeenCalledWith('r1');
      expect(c.reports).toHaveLength(0);
    });

    it('submitDelete surfaces a core-report error', () => {
      svc.deleteReport.mockReturnValue(throwError(() => new Error('x')));
      const c = make();
      c.openDeleteModal(report({ id: 'r1' }));
      c.submitDelete();
      expect(c.deleteError).toContain('Cannot delete');
      expect(c.deleteWorking).toBe(false);
    });
  });

  it('cellValue + paramInputType helpers map correctly', () => {
    const c = make();
    expect(c.cellValue({ a: null }, 'a')).toBe('—');
    expect(c.cellValue({ a: 5 }, 'a')).toBe('5');
    expect(c.paramInputType(param({ parameterType: 'DATE' }))).toBe('date');
    expect(c.paramInputType(param({ parameterType: 'NUMBER' }))).toBe('number');
    expect(c.paramInputType(param({ parameterType: 'STRING' }))).toBe('text');
  });
});
