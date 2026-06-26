import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { ReportsListComponent } from './reports-list';
import { ReportService, Report, ReportParameter } from './report.service';

type Svc = Record<
  'listReports' | 'runReport' | 'getExportUrl' | 'createReport' | 'deleteReport',
  ReturnType<typeof vi.fn>
>;

function param(over: Partial<ReportParameter> = {}): ReportParameter {
  return { id: 'p1', parameterName: 'branch', parameterType: 'STRING', optional: false, ...over };
}
function report(over: Partial<Report> = {}): Report {
  return {
    id: 'r1', reportName: 'Active Loans', reportType: 'Table', reportCategory: 'Loans',
    description: 'all active loans', coreReport: false, useReport: true,
    reportParameters: [param()], ...over,
  };
}

describe('ReportsListComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = {
      listReports: vi.fn().mockReturnValue(of([report()])),
      runReport: vi.fn().mockReturnValue(of([{ name: 'Loan A', balance: 100 }])),
      getExportUrl: vi.fn().mockReturnValue('http://x/runreports/Active%20Loans/export?format=csv'),
      createReport: vi.fn().mockReturnValue(of(report({ id: 'r2', reportName: 'New' }))),
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

  it('categories derive distinct sorted categories', () => {
    const c = make();
    c.reports = [
      report({ id: 'a', reportCategory: 'Savings' }),
      report({ id: 'b', reportCategory: 'Loans' }),
      report({ id: 'd', reportCategory: 'Loans' }),
    ];
    expect(c.categories).toEqual(['Loans', 'Savings']);
  });

  it('applyFilter narrows by category and search', () => {
    const c = make();
    c.reports = [
      report({ id: 'a', reportName: 'Loan Book', reportCategory: 'Loans', description: '' }),
      report({ id: 'b', reportName: 'Deposit Sum', reportCategory: 'Savings', description: '' }),
    ];
    c.categoryFilter = 'Loans';
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

    it('exportReport uses getExportUrl with the chosen format and params', () => {
      const openSpy = vi.spyOn(window, 'open').mockImplementation(() => null);
      const c = make();
      c.openRunModal(report());
      c.paramEntries[0].value = '001';
      c.exportFormat = 'xlsx';
      c.exportReport();
      expect(svc.getExportUrl).toHaveBeenCalledWith('Active Loans', 'xlsx', { branch: '001' });
      expect(openSpy).toHaveBeenCalled();
      openSpy.mockRestore();
    });
  });

  describe('create modal', () => {
    it('submitCreate is a no-op without name/sql', () => {
      const c = make();
      c.openCreateModal();
      c.submitCreate();
      expect(svc.createReport).not.toHaveBeenCalled();
    });

    it('submitCreate appends a report and re-filters', () => {
      const c = make();
      c.openCreateModal();
      c.formName = 'New';
      c.formSql = 'SELECT 1';
      c.submitCreate();
      expect(svc.createReport).toHaveBeenCalled();
      expect(c.reports).toHaveLength(2);
      expect(c.activeModal).toBeNull();
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
